# NAMING — single source of truth for project naming

> If you are about to rename anything, edit a path, or rebrand: read this file first.
> Update the **Migration Log** at the bottom whenever you change anything in the table.

Last reviewed: 2026-04-26

---

## The two names that matter

| Role | Value | Where it shows up | Mutability |
| --- | --- | --- | --- |
| **Internal codename** | `pantrie` | All code paths, Kotlin packages, backend Worker, D1 binding, repo dir, env vars, scripts | **Permanent.** Treat like a stable internal codename (think: Meta still uses "tao" internally). |
| **User-facing brand** | `Brimm` | App display name, marketing copy, Play Store listing, domain, email | **Mutable.** A future rebrand changes ONLY `Brand.kt` + `values/strings.xml` + `social/src/lib/brand.ts` (when added). |

Everything else either normalizes to `pantrie` (internal) or routes through one of the brand files (user-facing). No third name should exist anywhere in source.

---

## Locked surfaces — never rename, ever

These are baked into shipped infrastructure or external systems. Renaming any of these breaks production.

| Surface | Value | Why locked |
| --- | --- | --- |
| Android `applicationId` | `app.brimm` | Play Store identity. Changing orphans every install + loses the listing. |
| Backend Worker URL | `pantrie-backend.schulgenkyle.workers.dev` | Hardcoded into every shipped APK as `API_BASE_URL`. Changing breaks every installed app. |
| D1 database binding | `pantrie-db-staging` | Worker binding name. Renamable in wrangler.toml but requires migration + deploy. Don't unless necessary. |
| R2 bucket name | `pan-tree` | R2 buckets cannot be renamed. Photo data lives there. We hide this behind an env var (`R2_BUCKET`) so callers don't see the legacy name. |
| Domain | `brimmapp.com` | Registered + DNS configured. Rebrand later means new domain + 301 redirects, not a rename. |
| Support email | `support@brimmapp.com` | Tied to domain. |
| Pro subscription SKUs | `brimm_pro_monthly`, `brimm_pro_yearly` | Created in Play Console. Renaming = recreating, which orphans existing subscribers. (Former `brimm_pro_lifetime` SKU is deprecated and must be deactivated in Play Console — no lifetime tier offered.) |
| Play Console listing | `brimm-playstore-paste/` files on Desktop | Bound to Play Store entry. Rename the folder if you want; the contents are the only locked thing. |

---

## Mutable surfaces — normalize to canonical

These currently use one of the legacy names (`pantree`, `pan-tree`, or `brimm_*` for assets) and SHOULD be normalized.

| Current path / name | Becomes | Reason |
| --- | --- | --- |
| `C:/Users/12566/projects/pantree-landing/` | `pantrie-landing/` | Project folder name; not referenced by any locked system. |
| `C:/Users/12566/projects/pantree-social/` | `pantrie-social/` | Mission Control dashboard. Folder + its hardcoded path strings update together. |
| `C:/Users/12566/Downloads/PANTREE_ADMIN_KEY.txt` | `PANTRIE_ADMIN_KEY.txt` | Plus all 11 ingest scripts that read it. |
| `image_assets/brimm/` | `image_assets/pantrie/` | Asset master folder under repo. Brand-neutral. |
| Drawable prefix `brimm_<slug>.png` | `food_<slug>.png` (ingredients), `cuisine_<slug>.png`, `glass_<slug>.png`, `aisle_<slug>.png` | Content-based, brand-neutral. Future rebrand doesn't require renaming 124+ files. |
| Memory files `project_pantree_*.md` | `project_pantrie_*.md` | Plus content updated to reflect canonical naming. |

---

## Where brand strings live

If a string contains the word `Brimm` and is visible to a user, it lives in exactly one of these files. Nowhere else.

### Android (Kotlin + Compose)

- **`android/app/src/main/res/values/strings.xml`** — the canonical home for every user-facing string. Use `stringResource(R.string.app_name)` from Compose, or `getString(R.string.app_name)` from Kotlin.
- **`android/app/src/main/java/app/pantrie/Brand.kt`** — for brand strings that need to be referenced from non-Compose code (e.g. WorkManager notifications, error messages, log tags users might see). Just constants:

```kotlin
object Brand {
  const val APP_NAME = "Brimm"
  const val PRO_NAME = "Brimm Pro"
  const val DOMAIN = "brimmapp.com"
  const val SUPPORT_EMAIL = "support@brimmapp.com"
}
```

### Landing site (TypeScript)

- **`pantrie-landing/src/brand.ts`** (to create during normalization) — single export object:

```ts
export const BRAND = {
  name: 'Brimm',
  domain: 'brimmapp.com',
  supportEmail: 'support@brimmapp.com',
  tagline: '...',
};
```

All HTML/JSX references the export, never the literal string.

### Mission Control (TypeScript)

- **`pantrie-social/src/lib/brand.ts`** (to create during normalization) — same shape as landing.

### Backend (JavaScript Worker)

The backend has near-zero brand-facing surface (it returns JSON). Any user-visible string that does come back (purchase verification messages, error responses) goes through:

- **`backend/src/brand.js`** (to create during normalization) — same shape, exported as `BRAND`.

---

## How to rebrand in the future

If you ever rebrand from "Brimm" to anything else, this is the entire playbook:

1. Edit `android/app/src/main/java/app/pantrie/Brand.kt` — change `APP_NAME` and any other brand-tied constants.
2. Edit `android/app/src/main/res/values/strings.xml` — change `app_name`, store-listing strings, etc.
3. Edit `pantrie-landing/src/brand.ts` — change `BRAND.name` and friends.
4. Edit `pantrie-social/src/lib/brand.ts` — same.
5. Edit `backend/src/brand.js` — same.
6. Update `applicationId` in `android/app/build.gradle.kts` ONLY if creating a new Play Store listing (you usually keep the old one for continuity).
7. Buy new domain, set up DNS, configure email forwarding.
8. Migrate Pro SKUs (this is the painful part — Play Store SKUs cannot be renamed, you create new ones and grandfather old subscribers).
9. New Play Store listing assets: icon, feature graphic, screenshots.

Internal codename `pantrie`, the R2 bucket `pan-tree`, the backend URL, and the D1 binding all stay the same. No mass rename of Kotlin packages, no path migrations, no script updates.

---

## Why `pantrie` and not `brimm` as the internal codename

- `pantrie` is already in the Android namespace, the Kotlin package tree, the backend Worker name, the D1 binding, and the repo directory. Changing it would touch hundreds of files for zero functional benefit.
- The internal codename should outlive any single brand. If we rebrand from Brimm to something else next year, the code shouldn't have to change. `pantrie` is unbranded internally.
- `pan-tree` (with a hyphen) and `pantree` (the wrong spelling) are interim leftovers from the original rebrand. They have no business surviving in the codebase.

---

## Audit findings (2026-04-26)

Complete inventory by category — 186 files affected, 485+ references found.

| Category | Count | Disposition |
| --- | --- | --- |
| LOCKED-prod | 7 | KEEP (shipped infra) |
| LOCKED-listing | 8 | KEEP (Play Store + keystore) |
| INTERNAL-codename (already pantrie) | 156 | KEEP |
| USER-brand (already in canonical files) | 127 | KEEP for now; Phase I centralizes |
| ASSET-prefix (`brimm_*` PNG + 3 generators) | 26 + 3 | RENAME → content prefix |
| CONFIG-path (admin key + project folders + env vars) | ~20 | RENAME → pantrie |
| DOC-reference | 18 | UPDATE inline |
| LEGACY (`pan-tree` in TS prompts/outreach copy) | 14 | UPDATE inline |
| MEMORY-files (`project_pantree_*`) | 5 | RENAME → pantrie |

Audit transcript: `C:\Users\12566\AppData\Local\Temp\claude\C--Users-12566\38df1c7b-625d-40e1-b1ae-033c85db15dc\tasks\a6393b316b8df38c3.output`

---

## Phased Migration Plan

Phases ordered low-risk → high-risk. Each phase is independently verifiable. Update the Migration Log table when each phase completes.

### Phase A — Documentation (zero risk, no code changes)
- [x] Create NAMING.md
- [x] Add full audit findings to NAMING.md (this section)
- [ ] Update doc cross-references in `README.md`, `BETA_RUNBOOK.md`, `docs/play-console-setup.md`, `docs/demo-critique.md`, `demo/pantrie-demo.html` to use canonical names

### Phase B — Memory files (low risk, reversible)
- [ ] Rename `project_pantree.md` → `project_pantrie.md`
- [ ] Rename `project_pantree_landing.md` → `project_pantrie_landing.md`
- [ ] Rename `project_pantree_social.md` → `project_pantrie_social.md`
- [ ] Rename `project_pantree_playstore_status.md` → `project_pantrie_playstore_status.md`
- [ ] Rename `reference_pantree_secrets.md` → `reference_pantrie_secrets.md`
- [ ] Update `MEMORY.md` index to point to renamed files
- [ ] Sync content in renamed files (replace `pan-tree` / `pantree` in body with `pantrie` where applicable; preserve `Brimm` user-brand references)

### Phase C — Admin key file rename (low risk, mechanical)
- [ ] Rename `C:\Users\12566\Downloads\PANTREE_ADMIN_KEY.txt` → `PANTRIE_ADMIN_KEY.txt`
- [ ] Update 11 ingest scripts that hardcode the old path:
  - `backend/ingest/audit_v2_cocktails.cjs:10`
  - `backend/ingest/photo_backfill.cjs:27-28`
  - `backend/ingest/generate_recipe_images.cjs:46,50`
  - `backend/ingest/audit_v3_usda/fetch_v2.cjs:5`
  - `backend/ingest/audit_v3_cfg/fetch_v2.cjs:5`
  - `backend/ingest/audit_v3_hf/fetch.cjs:5`
  - `backend/ingest/audit_v3_hf/fetch_b.cjs:5`
  - `backend/ingest/audit_v3_hf/fetch_c.cjs:5`
  - `backend/ingest/audit_v3_hf/fetch_d.cjs:5`
  - `backend/ingest/audit_v3_hf/fetch_e.cjs:5`
  - `backend/ingest/audit_v3_tmdb/fetch_v2.cjs:5`
- [ ] Update `.gitignore:58`
- [ ] Update `docs/play-console-submission.md:249-250`

### Phase D — Env var renames in TypeScript code (low risk, text replacement)
- [ ] `PANTREE_BACKEND_URL` → `PANTRIE_BACKEND_URL` in `pantree-social/src/lib/admin-fetch.ts:28` + `src/app/traffic/page.tsx:9`
- [ ] `PANTREE_SHARE_URL` → `PANTRIE_SHARE_URL` in `pantree-social/src/scripts/post.ts:22`
- [ ] Update `.env` / `.env.example` files in pantree-social to reflect new names
- [ ] Heads-up: this requires the user to update their actual `.env` after rename, so app keeps reading the right values

### Phase E — Legacy `pan-tree` cleanup in TypeScript copy (low risk, prose only)
- [ ] `pantree-social/src/lib/anthropic.ts` — replace `pan-tree` / `pantree` mentions in AI prompt context with `Brimm` (user-facing) or `pantrie` (internal)
- [ ] `pantree-social/src/lib/creator-outreach.ts:6,62` — outreach brief copy → "Brimm"
- [ ] `pantree-social/src/lib/press-outreach.ts:7-8,67,77` — press brief copy → "Brimm"
- [ ] `pantree-social/src/lib/recipe-derive.ts:96` — headnote instruction → "Brimm"

### Phase F — Project folder renames (MEDIUM risk — coordinate with running processes)
- [ ] Verify no Next.js dev server / Mission Control process is currently running against `pantree-social/`
- [ ] Rename `C:\Users\12566\projects\pantree-landing\` → `pantrie-landing\`
- [ ] Rename `C:\Users\12566\projects\pantree-social\` → `pantrie-social\`
- [ ] Update Mission Control's own hardcoded path strings (file paths in `current_tracking.txt` source, social.db path references, scripts referencing old paths)
- [ ] Regenerate `current_tracking.txt` view
- [ ] Update memory files (Phase B) to reflect new project paths

### Phase G — Asset folder + drawable prefix rename (DEFERRED until image gen completes)
- [ ] Wait for background ingredient gen `bs55njdpz` to finish (currently 26/124 done, rate-limit-grinding)
- [ ] Rename `image_assets/brimm/` → `image_assets/pantrie/`
- [ ] Move drawable masters to content-prefixed subfolders
- [ ] Rename Android drawables: `brimm_<ingredient>.png` → `food_<ingredient>.png` (91 files), `brimm_cuisine_<x>.png` → `cuisine_<x>.png` (11), `brimm_glass_<x>.png` → `glass_<x>.png` (6), `brimm_aisle_<x>.png` → `aisle_<x>.png` (13)
- [ ] Rename generator scripts: `generate_brimm_images.cjs` → `generate_pantrie_images.cjs`, `generate_brimm_samples.cjs` → `generate_pantrie_samples.cjs`
- [ ] Update generator script internal paths + drawable prefix logic
- [ ] Update `IngredientEmoji.kt` (and future `IngredientImage.kt`) to reference new drawable names

### Phase H — Brand centralization (HIGHER effort, separate review)
- [ ] Create `android/app/src/main/java/app/pantrie/Brand.kt` with `APP_NAME = "Brimm"` and friends
- [ ] Audit every hardcoded `"Brimm"` in Kotlin files; replace with `Brand.APP_NAME` or `stringResource(R.string.app_name)`
- [ ] Create `pantrie-landing/src/brand.ts` (after Phase F rename) + audit landing TSX
- [ ] Create `pantrie-social/src/lib/brand.ts` (after Phase F rename) + audit social TS
- [ ] Create `backend/src/brand.js` for any user-visible backend strings (small surface, may not be needed)

---

## Migration Log

Append a row when you complete a phase or sub-step. Use ISO date.

| Date | Phase | Change | Verified by |
| --- | --- | --- | --- |
| 2026-04-26 | — | Created NAMING.md as canonical reference | initial |
| 2026-04-26 | A | Added full audit findings + phased plan to NAMING.md | this commit |
| 2026-04-26 | A | Doc cleanup: README.md launch checklist (`app.pantrie` → `app.brimm`, `pantrie.app` → `brimmapp.com`), BETA_RUNBOOK.md (3× pan-tree → Brimm), demo/pantrie-demo.html badge (PANTRIE → BRIMM) | content updated |
| 2026-04-26 | B | Renamed 5 memory files `project_pantree_*.md` → `project_pantrie_*.md`, `reference_pantree_secrets.md` → `reference_pantrie_secrets.md`. Synced content to use canonical naming. Updated MEMORY.md index. | ls verified |
| 2026-04-26 | C | Renamed `C:\Users\12566\Downloads\PANTREE_ADMIN_KEY.txt` → `PANTRIE_ADMIN_KEY.txt`. Updated 11 ingest scripts (audit_v2_cocktails.cjs, photo_backfill.cjs ×2 lines, generate_recipe_images.cjs ×2 lines, audit_v3_*/fetch*.cjs ×7 files), .gitignore, docs/play-console-submission.md. Env var `PANTREE_ADMIN_KEY` → `PANTRIE_ADMIN_KEY` everywhere. | ls + grep clean |
| 2026-04-26 | D | TS env vars: `PANTREE_BACKEND_URL` → `PANTRIE_BACKEND_URL` (admin-fetch.ts, traffic/page.tsx); `PANTREE_SHARE_URL` → `PANTRIE_SHARE_URL` (post.ts). User must update `.env` to match new var names. Default fallback URL in post.ts changed from `pantrie.app` → `brimmapp.com`. | edits applied |
| 2026-04-26 | E | TS legacy text cleanup: anthropic.ts (`PANTREE_BRIEF` → `BRIMM_BRIEF`, removed codename disclaimer, fixed Hacker News title + thread reply rules), creator-outreach.ts (`pan-tree` → `Brimm`, "Free Pro" → "Free Brimm Pro"), press-outreach.ts (`PANTREE_PRESS_BRIEF` → `BRIMM_PRESS_BRIEF`, sign-off team name), recipe-derive.ts (headnote rule). | edits applied |
| 2026-04-26 | F | **BLOCKED** — `mv pantree-landing` and `mv pantree-social` both failed with "Device or resource busy". Active processes (VSCode windows / npm dev servers) hold open file handles. User must close those processes for renames to proceed. | not yet run |
| _pending_ | F (post-unblock) | Rename `pantree-landing/` → `pantrie-landing/`, `pantree-social/` → `pantrie-social/`. Then grep renamed pantrie-social/ for any internal hardcoded `pantree-` path references and fix. | _waiting on user_ |
| _pending_ | G | Asset rename — wait for ingredient image gen to finish, then rename `image_assets/brimm/` → `image_assets/pantrie/` + drawable prefix migration | _waiting on image gen_ |
| _pending_ | H | Brand centralization (Brand.kt + per-project brand.ts) | _separate session_ |
| 2026-04-26 | rebrand | Brimm → Speakeater swap. Created `Brand.kt`, updated 15 hardcoded "Brimm" Kotlin strings → `Brand.APP_NAME`/`Brand.PRO_NAME`, updated `strings.xml` `app_name` → "Speakeater". | build verified |
| 2026-04-26 | rebrand | App launcher icon swapped to `s_pour_martini.png` (5 mipmap densities + adaptive 432x432 + Play Store 512x512). Adaptive icon background flipped from cream `#f9efe5` → charcoal `#0D0D0E`. | install verified |
| 2026-04-26 | redesign Phase 1 | Created `DESIGN_SYSTEM.md`. New theme (`Color.kt`, `Type.kt`, `Theme.kt`): dark editorial palette (Paper #020203, Ink #f4ecd9, Brass #a16207) + Playfair Display + Source Serif 4 + JetBrains Mono via Compose Google Fonts. `font_certs.xml` added. Splash + windowBackground flipped to black. | build verified |
| 2026-04-26 | walkthrough | First-launch tour shipped. `WalkthroughOverlay.kt` (spotlight cutout + tooltip), `TourRepository.kt` (DataStore-persisted `tour_completed_v1` flag), `WalkthroughViewModel.kt`, `TourSteps.kt` (7 steps). "Show app tour again" entry in Settings. | install verified |
| 2026-04-26 | redesign Phase 2 | All-black migration across 29 files. Hardcoded cream/light palette literals swapped for dark theme tokens. Bootlegger code (`vintageMode == true`, Sepia/SepiaInk, VintageWearOverlay) preserved untouched. | build verified |
| 2026-04-26 | emoji elimination | 104 emoji literals removed across 6 active files (glassEmoji → ""; placeholderGlyph → ""; 13 aisle emojis from AisleMeta; 🎉 / ★ / ✓ → Material icons). `BrandImage.kt` fallback rewritten — missing PNGs render brass placeholder dots / monogram squares, not emoji. | build verified |
| 2026-04-26 | Pro CTA | New `ProUpgradeCard.kt` self-contained 3-tier picker (no PaywallScreen redirect). Tap tier → tap Continue → Play Store sheet directly. Renders on Tonight empty state + Mixology (both Bootlegger sepia + Mixologist dark variants). | install verified |
| 2026-04-26 | Mixology ads | InlineAdCard cadence aligned with food deck via `SwipeQuotaRepository.AD_EVERY_N_SWIPES`. | install verified |
| 2026-04-26 | bug feedback | Wax-seal stamp animation on successful submission (brass circle scales in with overshoot + rotation, "LOGGED" mono caps + "you'll hear back" fade in, auto-dismiss after ~1.7s). | install verified |
| 2026-04-26 | emoji final purge | Deleted dead-code `IngredientEmoji.kt` (130+ emoji map entries). Replaced 1 `→` arrow in CommunityScreen with `Icons.Outlined.ChevronRight`. Removed `→` trailing arrow from MixologyScreen "flip for the story". | build verified |
| 2026-04-26 | speakeater.com config | Added `speakeater.com` to network_security_config.xml domain list (kept brimmapp.com for legacy + 301 redirects). wrangler.toml ALLOWED_ORIGIN already had speakeater.com pre-emptively. | applied |
