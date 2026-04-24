# pan-tree Privacy Policy (outline — fill in before publish)

_Last updated: [DATE]_

## 1. Who we are
pan-tree is operated by [LEGAL ENTITY NAME], registered at [ADDRESS]. Contact: privacy@pantrie.app.

## 2. What we collect

| Category | Data | Purpose |
|---|---|---|
| Identity | Email, name, Google account sub ID | Sign-in, account management |
| User content | Pantry items, reviews, recipe notes, photos | Core app functionality |
| App activity | Recipes saved/cooked/dismissed, meal plans, shopping list | Personalization |
| Diagnostics | Crash reports, anonymous usage analytics | App stability, product improvement |

We do **not** collect device IDs, location, contacts, calendar, SMS, or browsing history.

## 3. Why we collect it
- **Account identity**: required to sign you in and sync your data across devices (if you opt in to cloud sync)
- **Your content**: so you can access pantry, recipes, reviews across sessions
- **Activity data**: to personalize recommendations and remember what you've cooked
- **Diagnostics**: to fix crashes and improve the app; you can disable in Settings

## 4. Photos and AI processing

When you take a pantry photo and scan it, the photo is sent to our servers and forwarded to Anthropic (makers of Claude) for ingredient identification. Anthropic processes the image to return detected items and does not retain the image per our API agreement. We retain the detected-item list, not the photo itself, server-side. The original photo stays encrypted on your device unless you enable cloud sync.

Review photos: stored on your device by default (encrypted). Published only if you mark a review public.

## 5. How long we keep it

| Data | Retention |
|---|---|
| Account data (profile, pantry, reviews) | Until you delete your account |
| Crash logs | 90 days |
| Analytics events | 14 months |
| Server access logs | 30 days |
| Deleted account data | Hard-deleted within 30 days of deletion request |

## 6. Who we share with
- **Google**: sign-in (ID token), Play Billing (if you subscribe), Firebase Crashlytics (crash logs — PII redacted)
- **Anthropic**: pantry scan photos for ingredient identification
- **[Backend host]**: we host our backend on Cloudflare Workers (US). Your account data is processed there.
- **No data brokers. No advertising networks. No sale of personal data.**

## 7. Your rights
You can:
- **Access** your data: Settings → Download my data (JSON export)
- **Correct** your data: edit profile fields directly in the app
- **Delete** your account and all data: Settings → Delete account (or at `pantrie.app/delete-account`)
- **Opt out** of analytics and crash reporting: Settings → Privacy
- **Withdraw consent** for cloud sync: toggle off in Settings (we delete server-stored private content within 30 days)

For EU/UK users under GDPR: you have additional rights to object to processing and lodge a complaint with your supervisory authority.

For California users under CCPA: you have the right to know, delete, and opt out of sale. We do not sell your data.

Contact: privacy@pantrie.app — we respond within 30 days.

## 8. Security
- TLS 1.3 for all network traffic
- AES-256-GCM encryption for pantry data and photos on your device
- Android Keystore (hardware-backed when available) for encryption keys
- Short-lived session tokens with rotation
- Biometric confirmation for sensitive actions (account deletion, data export)

No system is 100% secure. If we experience a breach affecting your data, we will notify you within 72 hours per applicable law.

## 9. International transfers
If you use pan-tree outside the US, your data is transferred to and processed in the US. We rely on [Standard Contractual Clauses for EU→US transfers].

## 10. Children
pan-tree is not intended for users under 13 (COPPA) or 16 (GDPR). We do not knowingly collect data from children. If you believe we have, contact privacy@pantrie.app.

## 11. Changes to this policy
We will notify you in the app and update the "Last updated" date above. Continued use after changes constitutes acceptance.
