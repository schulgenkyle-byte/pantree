# Brimm — Beta Test Runbook

_see it. save it. savor it._

## 1. One-time backend setup

```bash
# From backend/
wrangler secret put ADMIN_KEY            # any long random string — you'll use it to view the dashboard
wrangler secret put BETA_DISCORD_WEBHOOK  # Discord → Server Settings → Integrations → Webhooks → New → Copy URL
wrangler d1 execute pantrie-db-staging --remote --file schema.sql
npm run deploy
```

## 2. Tester distribution (Play Console Internal Testing)

1. Play Console → **Testing → Internal testing → Create new release**
2. Upload the release AAB (Android Studio: **Build → Generate Signed Bundle**)
3. Under **Testers**: add tester emails (up to 100) or share the opt-in URL
4. Testers open the opt-in URL, accept, then install Brimm from Play Store (no review wait)

## 3. Admin dashboard

- URL: `https://pantrie-backend.schulgenkyle.workers.dev/admin/dashboard?key=YOUR_ADMIN_KEY`
- Shows: users, DAU/WAU, scans/cooks/saves (7d), top events, top recipes, last 50 feedback items
- Triage buttons on each feedback row flip status (open → triaged → fixed)

## 4. What testers see

- **Floating Feedback button** (bottom-right on every screen except Login). Opens a sheet with kind (Bug / Idea / Praise / Other), title, body, severity. Auto-attaches: route, app version, device.
- **Community tab** (5th bottom-nav tab) — anonymized "Someone just cooked X" ticker + trending-this-week list.
- All feedback hits Discord in ~2s with an embed: 🐛 BUG · MED: _title_ + body + user tag (last 4 of uuid).

## 5. Events tracked (no PII)

`app_opened`, `login_success`, `screen_view`, `tab_switched`, `recipe_saved`, `recipe_dismissed`, `recipe_cooked`, `beta_feedback_opened`, `beta_feedback_sent`.

Props are coarse: `recipeId`, `match%`, `route`. No names, no emails, no photos.

## 6. Tester invite template

> You're in the Brimm beta! See it. Save it. Savor it. Pantry-first cooking — scan what you have, swipe 10 recipes a night, cook, track waste.
>
> Install: [Play Store opt-in link]
>
> Please tap the floating **feedback** button any time something feels off or a feature would help. Keep titles short, body as detailed as you like. Everything reaches me instantly.
>
> Known limits: 1 scan/day free tier · recipes rotate every 24 h · English UI only.

## 7. Killing it after beta

```bash
wrangler secret delete BETA_DISCORD_WEBHOOK
wrangler secret delete ADMIN_KEY
```
Endpoints stay wired but admin routes 401 without the key, feedback still writes to D1 (keeps history).
