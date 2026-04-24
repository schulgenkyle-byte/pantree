# pan-tree Play Console Setup

End-to-end guide: nothing in hand → internal-testers installing the app. Budget ~90 minutes of active work, plus 1–3 calendar days of Google-side waits (identity verification, first-review propagation).

Package id: `app.pantrie` (permanent — do not change).
App name: pan-tree.
Developer: Kyle Schulgen (schulgenkyle@gmail.com).

---

## 1. Generate the release keystore (one time, ~5 min)

Do this once on the machine you plan to sign every release from.

```bash
cd "C:/Users/12566/Downloads/pantrie-build (1)/pantrie-build"
bash scripts/generate-keystore.sh
```

The script will:

1. Refuse to run if `android/pantrie-release.keystore` already exists (prevents bricking updates).
2. Prompt for keystore password, key password, name, org, country.
3. Generate `android/pantrie-release.keystore` (RSA 2048, 10000-day validity, alias `pantrie`).

Immediately after it finishes:

1. Save **keystore password** + **key password** into your password manager (1Password / Bitwarden / Google Password Manager).
2. Upload a copy of `android/pantrie-release.keystore` as a secure-file attachment in the same vault entry.
3. Upload a second copy to a cloud drive you control (Google Drive folder, not shared).
4. Confirm `git status` does not show the `.keystore` file as untracked — the root `.gitignore` already excludes `*.keystore`.

If you lose the keystore: **you cannot update the `app.pantrie` listing, ever.** You would have to publish a new listing under a new package id and ask all users to reinstall. Treat this file like a car title.

---

## 2. Build the signed AAB (~3 min)

From Git Bash:

```bash
export RELEASE_KEYSTORE="C:/Users/12566/Downloads/pantrie-build (1)/pantrie-build/android/pantrie-release.keystore"
export RELEASE_KEYSTORE_PW="xxx"
export RELEASE_KEY_ALIAS="pantrie"
export RELEASE_KEY_PW="xxx"
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd "C:/Users/12566/Downloads/pantrie-build (1)/pantrie-build/android"
/c/Users/12566/.gradle/wrapper/dists/gradle-8.9-bin/90cnw93cvbtalezasaz0blq0a/gradle-8.9/bin/gradle.bat :app:bundleRelease
```

Replace both `xxx` values with the real passwords you just saved.

Output:

```
android/app/build/outputs/bundle/release/app-release.aab
```

Verify:

1. File exists and is ~20–60 MB (smaller → something failed; larger → debug got mixed in).
2. Keep the file handy — you'll upload it in step 5.

Tip: do not paste the `export` block into a shared terminal or tmux session. Run it in a one-off shell, build, close the shell. Or put the four `RELEASE_*` values in a local `~/.pantrie-signing.env` file (gitignored, mode 600) and `source` it.

---

## 3. Play Console developer signup (~15 min + 1–2 day wait)

1. Go to https://play.google.com/console with the Google account you will use for publishing. Use `schulgenkyle@gmail.com` unless you have a dedicated publishing account.
2. Choose account type: **Personal** (you can convert to Organization later; Personal is fastest for a solo launch).
3. Pay the **$25 one-time registration fee** (credit card).
4. Accept the Developer Distribution Agreement.
5. Fill in developer profile: developer name (shown on the listing — suggest `pan-tree` or `Kyle Schulgen`), email, website if you have one, phone.
6. Submit identity verification. Google asks for a government ID + address. Verification usually clears in **1–2 business days**; can stretch to a week.

You cannot publish (even to internal testing) until identity verification is approved.

Decisions you must make during signup:

- **Country of residence** — tax + payout implications. Pick your actual country.
- **Developer display name** — user-visible on the listing. Changing it later requires support ticket.
- **Account type** (Personal vs Organization) — Organization requires a D-U-N-S number and takes longer.

---

## 4. Create the app listing (~30 min)

In Play Console: **All apps → Create app**.

Initial fields:

- App name: `pan-tree`
- Default language: `English (United States) – en-US`
- App or game: **App**
- Free or paid: **Free** (in-app purchases will come later via Play Billing)
- Declarations: confirm Developer Program Policies + US export laws

Click **Create app**. You'll land on the dashboard with a checklist of required forms. Work through each, referencing the material a separate agent is producing at `docs/playstore-listing.md` (short description, full description, feature graphic, screenshots, content rating questionnaire, data-safety answers, etc.).

Required before you can ship to internal testing:

- [ ] App access (is the app behind a login? — yes, provide a test account)
- [ ] Ads declaration
- [ ] Content rating questionnaire
- [ ] Target audience and content
- [ ] News app declaration (no)
- [ ] COVID-19 contact tracing (no)
- [ ] Data safety form (see `docs/data-safety-draft.md`)
- [ ] Government app (no)
- [ ] Financial features (no)
- [ ] Health (no)
- [ ] Privacy policy URL (hosted version of `docs/privacy-policy-outline.md`)
- [ ] App category: **Food & Drink**
- [ ] Contact details (email, phone optional, website optional)
- [ ] Store listing: short description, full description, app icon, feature graphic, phone screenshots (min 2), tablet screenshots optional

Tip: the store-listing assets (icon, feature graphic, screenshots) can be placeholders for internal testing — you only need to lock them before moving to closed/open testing or production.

---

## 5. Internal Testing track (~15 min)

Internal testing is the fastest path: up to 100 testers, near-instant rollout after the first-review delay, no closed-testing review cycle.

1. Play Console → **Testing → Internal testing → Create new release**.
2. **App signing**: on the first release Google asks whether you want to use **Play App Signing** (recommended — Google holds the upload key).
   - For pan-tree: **enroll in Play App Signing** and **upload your keystore as the upload key**.
   - Alternatively, let Google generate a fresh app signing key and use your `pantrie-release.keystore` as the upload key only. Either works; enrolling in Play App Signing means Google can help if you ever lose the upload key.
3. Upload `app-release.aab` from step 2.
4. Fill in **Release name** (auto-filled from versionName, `0.1.0`) and **Release notes** (plain text, one set of notes in en-US, e.g. "Initial internal beta.").
5. Click **Save → Review release → Start rollout to Internal testing**. Confirm.
6. Tab **Testers** → **Create email list** → paste tester emails (one per line, max 100). Save the list, then attach it to the internal track.
7. Scroll to **How testers join your test** → copy the **opt-in URL** (`https://play.google.com/apps/internaltest/...`).
8. Send each tester:
   - The opt-in URL.
   - Instruction: open the URL on the Android device signed in with the same Google account as the invite, tap **Become a tester**, then tap the Play Store link to install.
   - Note: testers must accept the invite **before** the Play Store link will show the app.

---

## 6. First-review delay

On the **first release** for a new listing — even internal testing — Play runs a policy/bot review before the build becomes installable. Expect **24–72 hours**, occasionally longer. The dashboard will show "In review". Do not re-upload; that resets the timer.

Subsequent internal-testing releases typically go live within minutes to a couple of hours.

If review stalls >5 days, check Play Console → Policy → Messages for a reviewer note and reply there.

---

## 7. Signing-key backup — CRITICAL

Before you tell anyone the app exists:

1. [ ] `android/pantrie-release.keystore` stored in password manager **and** cloud drive (two independent copies).
2. [ ] Keystore password + key password stored in the same password manager entry.
3. [ ] `.gitignore` coverage verified (`*.keystore` matched — already done in this repo).
4. [ ] A short note in your password manager describing: file location, alias (`pantrie`), which Google account owns the Play Console listing.
5. [ ] (Optional but recommended) Enroll in Play App Signing in step 5 so Google can help recover if the upload key is lost.

If the keystore is lost **and** you did not enroll in Play App Signing: the `app.pantrie` package id is permanently unusable. You'll have to publish a new listing (e.g. `app.pantrie2`) and ask every user to uninstall + reinstall. Do not rely on being able to recover this later — back it up today.

---

## Open decisions (user action)

The following cannot be answered by this doc and require your input during signup/listing:

- **Play Console country of residence** — pick your real country (tax/payout impact).
- **Account type** — Personal (recommended for solo launch) vs Organization (requires D-U-N-S, slower).
- **Developer display name** — `pan-tree` vs `Kyle Schulgen` vs other. User-visible, hard to change.
- **Bank account + tax form** — not required for Free apps, but required before you can take Pro subscription revenue. Set up under **Setup → Payments profile** when you're ready for paid features.
- **Privacy policy hosting URL** — `docs/privacy-policy-outline.md` needs to be published at a stable public URL (Cloudflare Pages, GitHub Pages, etc.) before the listing will accept it.
- **Test account credentials** — Play requires a working login if the app is gated. Create a dedicated `pantrie-review@...` Google account with a seeded pantry and put credentials in the App access form.
