// Google Play billing. Real Play Developer API verification for subscriptions.
// Uses service account credentials (env.PLAY_SERVICE_ACCOUNT_JSON).

import { json, err, readJson, validPurchaseToken, validString, sha256Hex } from './util.js';
import { getServiceAccountToken } from './google-auth.js';
import { verifyGoogleSignedJwt } from './jwks.js';
import { enforce, clientIp } from './ratelimit.js';

// Canonical SKU allow-list. Add new SKUs here BEFORE announcing them.
const ALLOWED_SKUS = new Set([
  'pantrie_pro_monthly',
  'pantrie_pro_annual',
]);

async function verifyPurchaseWithGoogle(env, sku, purchaseToken) {
  const accessToken = await getServiceAccountToken(env, 'https://www.googleapis.com/auth/androidpublisher');
  if (!accessToken) return { ok: false, reason: 'no-sa-token' };
  const pkg = env.PLAY_PACKAGE_NAME;
  if (!pkg) return { ok: false, reason: 'no-package' };

  const url = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(pkg)}/purchases/subscriptions/${encodeURIComponent(sku)}/tokens/${encodeURIComponent(purchaseToken)}`;
  const res = await fetch(url, { headers: { Authorization: `Bearer ${accessToken}` } });
  if (res.status === 404) return { ok: false, reason: 'not-found' };
  if (!res.ok) return { ok: false, reason: `http-${res.status}` };
  const data = await res.json();

  // paymentState: 0 payment pending, 1 received, 2 free trial, 3 pending deferred
  if (data.paymentState !== 1 && data.paymentState !== 2 && data.paymentState !== 3) {
    return { ok: false, reason: `payment-state-${data.paymentState}` };
  }
  const expiryMs = parseInt(data.expiryTimeMillis || '0', 10);
  if (!expiryMs || expiryMs < Date.now()) {
    return { ok: false, reason: 'expired' };
  }
  if (data.cancelReason === 1) {
    // System cancelled / billing error. Refuse.
    return { ok: false, reason: 'cancelled-system' };
  }
  return {
    ok: true,
    expiresAt: expiryMs,
    autoRenewing: !!data.autoRenewing,
    obfuscatedExternalAccountId: data.obfuscatedExternalAccountId || null,
    acknowledgementState: data.acknowledgementState, // 0 yet to acknowledge, 1 acknowledged
    paymentState: data.paymentState,
  };
}

async function acknowledgePurchase(env, sku, purchaseToken) {
  const accessToken = await getServiceAccountToken(env, 'https://www.googleapis.com/auth/androidpublisher');
  if (!accessToken) return false;
  const pkg = env.PLAY_PACKAGE_NAME;
  if (!pkg) return false;
  const url = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(pkg)}/purchases/subscriptions/${encodeURIComponent(sku)}/tokens/${encodeURIComponent(purchaseToken)}:acknowledge`;
  const res = await fetch(url, {
    method: 'POST',
    headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({}),
  });
  return res.ok;
}

export const handleBilling = {
  async verify(request, userId, env) {
    const rl = await enforce(env, 'billing', userId);
    if (rl) return rl;

    const p = await readJson(request, 4_000);
    if (p.error) return p.error;
    const { purchaseToken, productId } = p.value;

    if (!validPurchaseToken(purchaseToken)) return err(400, 'purchaseToken invalid');
    if (!validString(productId, { min: 1, max: 128 }) || !ALLOWED_SKUS.has(productId)) return err(400, 'unknown sku');

    // Already bound to another user?
    const tokenHash = await sha256Hex(purchaseToken);
    const existingBinding = await env.DB.prepare(
      'SELECT user_id FROM entitlement WHERE purchase_token_hash = ?'
    ).bind(tokenHash).first();
    if (existingBinding && existingBinding.user_id !== userId) {
      return err(409, 'purchase token already bound to a different account');
    }

    // In dev (or when service account not configured), stop here with a clear error.
    if (!env.PLAY_SERVICE_ACCOUNT_JSON || !env.PLAY_PACKAGE_NAME) {
      return err(503, 'billing not configured on server');
    }

    const result = await verifyPurchaseWithGoogle(env, productId, purchaseToken);
    if (!result.ok) return err(403, `purchase not valid: ${result.reason}`);

    // Optional: bind to the Pantrie userId via obfuscatedExternalAccountId.
    // The Android client SHOULD set obfuscatedAccountId = userId when launching billing.
    if (result.obfuscatedExternalAccountId) {
      // Google returns the server-provided obfuscated id; accept only if matches our userId hash.
      // Using sha256(userId) avoids leaking userId to Google Play.
      const expected = await sha256Hex(userId);
      if (result.obfuscatedExternalAccountId !== expected) {
        return err(403, 'purchase bound to different pantrie account');
      }
    }

    const expiresAt = result.expiresAt;
    const autoRenewing = result.autoRenewing ? 1 : 0;

    await env.DB.prepare(
      `INSERT INTO entitlement (user_id, sku, purchase_token_hash, expires_at, auto_renewing, updated_at)
       VALUES (?, ?, ?, ?, ?, ?)
       ON CONFLICT(user_id) DO UPDATE SET
         sku=excluded.sku,
         purchase_token_hash=excluded.purchase_token_hash,
         expires_at=excluded.expires_at,
         auto_renewing=excluded.auto_renewing,
         updated_at=excluded.updated_at`
    ).bind(userId, productId, tokenHash, expiresAt, autoRenewing, Date.now()).run();

    // Acknowledge (required within 3 days per Play policy)
    if (result.acknowledgementState === 0) {
      await acknowledgePurchase(env, productId, purchaseToken).catch(() => {});
    }

    return json({ valid: true, entitlement: { sku: productId, expiresAt, autoRenewing: !!autoRenewing } }, 200, request, env);
  },

  async entitlement(userId, env, request) {
    const row = await env.DB.prepare(
      'SELECT sku, expires_at, auto_renewing FROM entitlement WHERE user_id = ? AND expires_at > ?'
    ).bind(userId, Date.now()).first();
    if (!row) return json({ active: false }, 200, request, env);
    return json({ active: true, sku: row.sku, expiresAt: row.expires_at, autoRenewing: !!row.auto_renewing }, 200, request, env);
  },

  /**
   * Real-Time Developer Notifications (Pub/Sub push).
   * Verifies the OIDC bearer issued by Google Pub/Sub and re-verifies purchase state.
   */
  async rtdn(request, env) {
    const ip = clientIp(request);
    const rl = await enforce(env, 'rtdn', ip);
    if (rl) return rl;

    // OIDC check
    const authHeader = request.headers.get('authorization') || '';
    if (!authHeader.startsWith('Bearer ')) return err(401, 'missing bearer');
    const oidcToken = authHeader.slice(7);
    const audience = env.RTDN_AUDIENCE;
    const expectedEmail = env.RTDN_SA_EMAIL;
    if (!audience || !expectedEmail) return err(503, 'rtdn not configured');
    const verified = await verifyGoogleSignedJwt(oidcToken, env, {
      audience,
      email: expectedEmail,
      issuers: ['https://accounts.google.com', 'accounts.google.com'],
    });
    if (!verified) return err(401, 'invalid oidc');

    const p = await readJson(request, 16_000);
    if (p.error) return p.error;
    const message = p.value.message;
    if (!message?.data) return json({ ok: true }, 200, request, env);

    let decoded;
    try { decoded = JSON.parse(atob(message.data)); } catch { return err(400, 'bad message.data'); }

    const sub = decoded.subscriptionNotification;
    if (!sub || !sub.purchaseToken || !sub.subscriptionId) return json({ ok: true }, 200, request, env);

    // Re-verify purchase with Google and update/revoke entitlement row.
    const verifyRes = await verifyPurchaseWithGoogle(env, sub.subscriptionId, sub.purchaseToken).catch(() => ({ ok: false }));
    const tokenHash = await sha256Hex(sub.purchaseToken);
    const row = await env.DB.prepare('SELECT user_id FROM entitlement WHERE purchase_token_hash = ?').bind(tokenHash).first();
    if (!row) return json({ ok: true }, 200, request, env); // unknown token — ignore

    if (!verifyRes.ok) {
      // Revoke: set expiry to now
      await env.DB.prepare('UPDATE entitlement SET expires_at = ?, auto_renewing = 0, updated_at = ? WHERE purchase_token_hash = ?')
        .bind(Date.now(), Date.now(), tokenHash).run();
    } else {
      await env.DB.prepare('UPDATE entitlement SET expires_at = ?, auto_renewing = ?, updated_at = ? WHERE purchase_token_hash = ?')
        .bind(verifyRes.expiresAt, verifyRes.autoRenewing ? 1 : 0, Date.now(), tokenHash).run();
    }

    return json({ ok: true }, 200, request, env);
  },

  ALLOWED_SKUS,
};
