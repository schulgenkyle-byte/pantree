# Curate-a-Party — Feature Spec

Internal product spec. Voice rules off. This is what we're building.

> **2026-05-12 supersession note:** The pricing model in this doc is stale.
> Canonical pricing lives in `CURRENT_TRUTHS.md` and the MVP build contract is
> in `_CURATE_A_PARTY_BUILD_SPEC.md`. In short: **$5 per menu retail post-launch
> (not $3.99 or $4)**, **Pro at $4.99/mo or $45/yr retail**, **$30/yr Kickstarter
> founder rate grandfathered forever** (capped 500 backers), **no lifetime tier
> anywhere**. The per-menu Play Console SKU when it ships is `menu_unlock_single`
> at $4.99 (closest standard tier to $5). Single SKU, Pro-bundled access path A.

---

## User-facing surface

**Entry point:** new "Parties" tab in the bottom navigation, between Mixology and Saved. Tab icon: an Art Deco fan glyph in BrassBright.

**Landing screen:** scrollable grid of menu cards. Each card shows hero image, title, era (e.g. "1925 · New York"), and price chip ($1 Pro / $4 non-Pro). Free Pro-Preview menu unlocked for everyone at install ("The Welcome Toast" — single cocktail + appetizer to demo the format).

**Menu detail screen:** hero image at top, title + era + bar attribution, then five tabs in a sticky tab row:
1. **The Night** — narrative intro + host timeline
2. **Drinks** — 5 cocktails, each links to existing Mixology recipe screen
3. **Food** — 6 small plates, each links to existing Recipe detail screen
4. **Shopping** — consolidated grocery list with quantity math + bar of "already in your pantry" green checks
5. **Notes** — music, dietary substitutions, faithful-vs-modern variants

**Purchase flow:** tapping a locked menu opens Play Billing sheet with the appropriate SKU. Backend verifies + writes party_menu_purchase row + returns unlock token. Menu opens in detail view automatically.

**PDF export:** every purchased menu has an "Export PDF" button. Server generates a sepia-themed PDF (Bootlegger aesthetic), 6-8 pages, full menu + recipes + shopping list. PDF cached in R2 under `pan-tree/menus/<user_id>/<menu_id>.pdf`. Backup against app deletion (per Kickstarter promise: menus survive on user's device).

---

## Data model

Three new D1 tables.

```sql
CREATE TABLE party_menu (
  id              TEXT PRIMARY KEY,
  slug            TEXT NOT NULL UNIQUE,
  title           TEXT NOT NULL,
  era_year        INTEGER,           -- e.g. 1928
  era_city        TEXT,              -- e.g. 'New York'
  era_bar         TEXT,              -- e.g. 'Long Island estate'
  description     TEXT,              -- 2-3 sentence narrative intro
  hero_image_url  TEXT,
  host_timeline   TEXT,              -- markdown
  music_note      TEXT,
  status          TEXT DEFAULT 'draft',  -- draft | published
  is_world_tour   INTEGER DEFAULT 0,     -- 1 for stretch goal menus
  created_at      INTEGER NOT NULL,
  published_at    INTEGER
);

CREATE TABLE party_menu_item (
  menu_id       TEXT NOT NULL,
  sort_order    INTEGER NOT NULL,
  recipe_id     TEXT NOT NULL,
  role          TEXT NOT NULL,         -- 'cocktail' | 'food' | 'snack' | 'dessert'
  serving_note  TEXT,                  -- e.g. "12 halves per 12 guests"
  PRIMARY KEY (menu_id, sort_order),
  FOREIGN KEY (menu_id) REFERENCES party_menu(id),
  FOREIGN KEY (recipe_id) REFERENCES recipe(id)
);

CREATE TABLE party_menu_purchase (
  user_id          TEXT NOT NULL,
  menu_id          TEXT NOT NULL,
  price_paid_cents INTEGER NOT NULL,
  purchase_token   TEXT,                  -- Play Billing token; NULL for Kickstarter-comp grants
  granted_by       TEXT,                  -- 'purchase' | 'kickstarter' | 'pro_bundle'
  purchased_at     INTEGER NOT NULL,
  pdf_generated_at INTEGER,
  PRIMARY KEY (user_id, menu_id),
  FOREIGN KEY (menu_id) REFERENCES party_menu(id)
);

CREATE INDEX idx_party_menu_status ON party_menu(status) WHERE status = 'published';
CREATE INDEX idx_party_menu_purchase_user ON party_menu_purchase(user_id);
```

---

## Pricing + Play Console

Per-menu pricing model requires Play Console one-time in-app product SKUs:

- `menu_unlock_pro` ($1.00) — single menu unlock for Pro users
- `menu_unlock_free` ($4.00) — single menu unlock for non-Pro users

OR (cleaner): Play Console doesn't easily support discount tiers per SKU. Two operational paths:

**Path A (recommended): subscription bundling.** Pro members get ALL menus included as part of $4.99/mo. Non-Pro buys individual menus at $4 via consumable Play SKUs. KS marketing copy says "$1 a piece for Pro members" meaning "any of the menus, included free as part of $4.99/mo Pro — works out to $1 per menu if you only download four." Honest, simpler.

**Path B (literal): two SKUs per menu.** `menu_<slug>_pro` and `menu_<slug>_free`. 100 SKUs total at 50 menus. Burdensome for Play Console management. Not recommended.

Recommend Path A. Marketing copy aligns: "$1 per menu for Pro members (bundled in your subscription, average cost per menu downloaded), $4 per menu for non-subscribers."

For Path A, only ONE new SKU is needed:
- `menu_unlock_single` ($3.99) — non-Pro single-menu unlock

Pro tier check happens server-side before menu access is granted.

---

## Backend endpoints

```
GET  /menus                         — list published menus (paginated, public)
GET  /menus/:slug                   — menu detail (public for cover, paywalled for full)
POST /menus/:slug/unlock-free       — Pro user grants menu via subscription bundle
POST /menus/verify-purchase         — non-Pro single-menu unlock; verifies Play purchase token
GET  /menus/:slug/pdf               — signed-URL redirect to R2 PDF (purchased users only)
GET  /me/menus                      — list of menus user has unlocked
POST /admin/menus                   — create/edit menu (admin-only via X-Admin-Key)
```

---

## What this campaign funds

Build estimate for the feature (solo dev):

- Data model + migration: 2 days
- Admin menu authoring UI: 5 days (web, runs inside admin dashboard)
- Android Parties tab + menu detail screen: 7 days
- Purchase flow + Play SKU wire-up: 3 days
- PDF generation pipeline: 4 days (HTML-to-PDF via puppeteer-on-Worker or pre-baked via @react-pdf/renderer in Cloudflare Worker)
- Five seed menus written by hand: 5 days (one menu = one day of historical research + writing + recipe matching)
- Remaining 45 menus on 10/month cadence: 22 weeks post-launch

Total in-session before Play Store launch: ~26 working days for code + 5 days for first 5 menus = 31 working days = 6 calendar weeks at sustainable solo-dev pace.

Per-menu effort post-launch: 4 hours each (the menu authoring UI accelerates this dramatically).

---

## Dependencies

- Existing `recipe` table (food + cocktail rows already exist; 5,036 cocktails and 23,743 foods)
- Existing R2 bucket `pan-tree` (PDFs land here)
- Existing Play Billing flow (works as of 2026-05-12 billing fix)
- New SKU created in Play Console: `menu_unlock_single` ($3.99)
- New `EntitlementRepository` method: `hasMenu(menuId): Boolean` (combines Pro bundle + individual purchases)

---

## Risk surfaces

1. **PDF generation cost.** Each PDF render hits HTML rendering compute. Cache in R2 on first generation, regenerate only on menu edit. Cost target: under $0.001 per PDF.
2. **Recipe linkage drift.** Menus reference `recipe.id` foreign keys. If a recipe is deleted, menu breaks. Mitigation: soft-delete recipes referenced by any published menu.
3. **Pro-bundle vs single-purchase fairness.** Pro users who later downgrade keep their downloaded menus (granted_by='pro_bundle' rows persist). Treated as a one-way ratchet to keep things simple.

---

## Launch checklist (post-campaign)

- [ ] D1 migration applied to staging + prod
- [ ] Admin menu authoring UI shipped
- [ ] 5 seed menus written, recipe-linked, image-paired, host-timeline-written
- [ ] Android Parties tab built and merged
- [ ] Play Console SKU `menu_unlock_single` created and activated
- [ ] PDF generator tested with all 5 seed menus
- [ ] Backend endpoints deployed
- [ ] Kickstarter backer grants script run (grants menus to backers based on tier)
- [ ] Backers notified via email with redemption instructions
