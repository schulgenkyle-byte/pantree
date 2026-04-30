package app.pantrie

/**
 * Single source of truth for user-facing brand strings. See pantrie-build/NAMING.md.
 *
 * Mirrors the schema used in pantree-social/src/lib/brand.ts (the social manager's
 * BRAND object) so a future rebrand changes EXACTLY these constants + strings.xml +
 * the per-project brand.ts files. Internal codename pantrie is permanent and never
 * appears here.
 *
 * Two-tier email model:
 *   SUPPORT_EMAIL stays brimmapp.com forever — bound to the privacy policy that
 *   every shipped APK references. Rebranding it would break the legal commitment to
 *   existing users.
 *   HELLO_EMAIL is the marketing surface tied to the new brand domain. Use this for
 *   any new copy that asks users to "reach out" or "say hi".
 *
 * SKU ids (brimm_pro_monthly/yearly/lifetime) live in BillingManager.kt and stay
 * locked — Play Console SKU ids cannot be renamed without orphaning subscribers.
 */
object Brand {
  const val APP_NAME = "Speakeater"
  const val PRO_NAME = "Speakeater Pro"
  const val TAGLINE = "see it. save it. savor it."
  const val DOMAIN = "speakeater.com"
  const val LEGACY_DOMAIN = "brimmapp.com"
  const val SUPPORT_EMAIL = "support@brimmapp.com"
  const val HELLO_EMAIL = "hello@speakeater.com"
}
