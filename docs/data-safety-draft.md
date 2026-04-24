# Play Console — Data Safety form draft

Use these exact answers in the Data Safety section.

## Does your app collect or share any of the required user data types?

**Yes.** We collect personal info, app activity, and app diagnostics. See below.

---

## Personal info

### Name
- **Collected**: Yes
- **Shared**: No
- **Optional**: Yes (user can leave blank; Google sign-in returns a default)
- **Purpose**: Account management, app functionality (display in reviews/feed if public)
- **Encrypted in transit**: Yes (TLS 1.3)
- **Users can request deletion**: Yes (Settings → Delete account)

### Email address
- **Collected**: Yes
- **Shared**: No
- **Optional**: No (required for Google Sign-In)
- **Purpose**: Account management
- **Encrypted in transit**: Yes
- **Users can request deletion**: Yes

### User IDs
- **Collected**: Yes (internal user ID + Google `sub` subject ID)
- **Shared**: No
- **Optional**: No
- **Purpose**: Account management, app functionality
- **Encrypted in transit**: Yes
- **Users can request deletion**: Yes

---

## Photos and videos

### Photos
- **Collected**: Yes
- **Shared**: No (by default; shared only if user explicitly marks a review public)
- **Optional**: Yes (scan is optional; manual add works instead)
- **Purpose**: App functionality (pantry recognition via Claude Vision), personal journal (dish photos)
- **Encrypted in transit**: Yes (TLS 1.3 to Cloudflare Worker → Anthropic API)
- **Encrypted at rest**: Yes (device: `EncryptedFile` AES-256-GCM-HKDF; backend: object storage server-side encryption)
- **Users can request deletion**: Yes (per-photo + full account deletion)

Important: Photos sent to Claude (Anthropic) via vision API for ingredient detection. Disclose this in privacy policy.

---

## App activity

### App interactions
- **Collected**: Yes (recipe interactions: save/swipe/cook/dismiss, shopping list actions, plan creation)
- **Shared**: No
- **Optional**: No (required for core features)
- **Purpose**: App functionality, personalization
- **Encrypted in transit**: Yes

### In-app search history
- **Collected**: No

### Installed apps
- **Collected**: No

### Other user-generated content
- **Collected**: Yes (review text, recipe notes, pantry item names)
- **Shared**: Only if user marks review public
- **Purpose**: App functionality, optional social feature
- **Users can request deletion**: Yes (delete individual item/review + full account)

---

## App info and performance

### Crash logs
- **Collected**: Yes (Firebase Crashlytics)
- **Shared**: With Google (processor)
- **Optional**: Yes (Settings → Privacy → Analytics opt-out)
- **Purpose**: App functionality (stability)
- **Encrypted in transit**: Yes
- **PII redacted**: Yes (custom redactor strips emails, file paths)

### Diagnostics
- **Collected**: Yes (performance metrics, anonymous usage events)
- **Shared**: With analytics provider only (Firebase / PostHog)
- **Optional**: Yes
- **Purpose**: App functionality, product improvement

### Other app performance data
- **Collected**: No

---

## Device or other IDs

### Device or other IDs
- **Collected**: No (we use pseudonymous app-install UUID generated locally; not a device ID)

---

## Financial info

Not collected. Google Play handles all payments.

---

## Health and fitness

### Health info
- **Collected**: No (dietary preferences like "vegan" are not health data per Play definitions)

Note: Check latest Play policy if adding calorie tracking — that flips this to yes.

---

## Location

Not collected.

## Web browsing

Not collected.

## Contacts

Not collected.

## Audio

Not collected.

## Files and docs

Not collected (we use Photo Picker, not file access).

## Calendar

Not collected.

## Messages

Not collected.

---

## Security practices

- **Is data encrypted in transit?** Yes
- **Do you provide a way for users to request that their data is deleted?** Yes (in-app Settings → Delete account + web URL)
- **Has your app been independently validated against a global security standard?** No at launch; plan MASVS L1 third-party assessment post-launch
- **You follow Play Families Policy**: Not applicable (app is not targeted at children)

---

## Data deletion URL (required)

`https://pantrie.app/delete-account` — must be live before first production submission. Page should:
- Let the user sign in with Google (server-to-server — no client JS auth leak)
- Show account summary (email, join date)
- "Delete my account" button with confirm modal
- On confirm: mark soft-deleted, schedule hard delete within 30 days, revoke sessions
- Send confirmation email

---

## Privacy policy URL (required)

`https://pantrie.app/privacy` — see `privacy-policy-outline.md`.
