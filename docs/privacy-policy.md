# pan-tree Privacy Policy

**Last updated:** April 24, 2026

pan-tree ("we", "our", "the app") is a pantry-first cooking assistant operated by Kyle Schulgen. This policy describes what data we collect, why, and your rights.

## 1. What we collect

**Account data** — when you sign in with Google:
- Google account email address and unique identifier (sub)
- Profile display name (if provided)

**Pantry and usage data** — generated as you use the app:
- Items you add to your pantry, including quantities, units, and expiration dates
- Photos of receipts, fridge/pantry contents, and barcodes you submit to the scan feature (processed for ingredient extraction, then discarded)
- Photos you submit with recipe contributions (retained if your recipe is approved for the public feed, discarded otherwise)
- Shopping list items, meal plans, recipe swipes (save/skip/cook), reviews you write, and follow/block relationships

**Analytics** — basic product-usage events:
- Screen views, taps, swipes, and button presses, associated with your account ID
- Session metadata: app version, device model, and coarse timing

**Purchase data** — if you buy a subscription:
- Google Play purchase token (for entitlement verification)
- Subscription tier and renewal status

We do **not** collect precise location, contacts, SMS, call logs, health data, or biometric identifiers.

## 2. Why we collect it

- **Provide the service:** recipe matching, pantry tracking, meal planning, scanning, shopping list
- **Improve the service:** measure which features get used, fix bugs, improve recipe ranking
- **Community:** show your reviews and approved recipe submissions to other users (your display name shown, not your email)
- **Account security:** detect abuse, enforce rate limits, rotate compromised credentials

We do **not** sell your data to anyone. We do not share it with advertisers or data brokers.

## 3. Where your data lives

- Account and usage data: Cloudflare Workers + D1 (SQLite) hosted in Cloudflare's global network
- Photo uploads (approved recipe contributions only): Cloudflare R2
- OCR / vision processing: Anthropic Claude API (images are processed to extract text, not retained by Anthropic per their API terms)
- Google Play billing data: Google Play services

## 4. Retention

- Pantry, shopping, plan, and interaction data: retained while your account exists
- Receipt/fridge scan photos: discarded within 24 hours of processing
- Unapproved recipe submission photos: discarded within 30 days
- Approved recipe submissions: retained until the recipe is deleted
- Analytics events: retained 90 days
- Deleted account data: purged within 30 days of account deletion

## 5. Your rights

You can:
- **Export your data** — in-app: Settings → Export my data
- **Delete your account** — in-app: Settings → Delete account. This permanently removes your pantry, plans, shopping list, interactions, reviews, and account record within 30 days.
- **Contact us** about anything in this policy: **schulgenkyle@gmail.com**

## 6. Children

pan-tree is not directed to children under 13. We do not knowingly collect data from children under 13. If you believe a child has submitted data, contact us and we will delete it.

## 7. Security

- TLS in transit for all API requests
- Certificate pinning in the Android client
- HMAC-signed access tokens with rotating refresh tokens
- EXIF/GPS metadata stripped from user-submitted photos before storage
- Access to backend systems is limited to the app operator

No security is perfect. Report vulnerabilities to schulgenkyle@gmail.com and we will acknowledge within 7 days.

## 8. Changes

We will post material changes here with an updated "Last updated" date. If changes affect how we use data already collected, we will notify active users in-app before the change takes effect.

## 9. Contact

Kyle Schulgen — schulgenkyle@gmail.com
