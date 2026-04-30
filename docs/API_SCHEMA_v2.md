# API_SCHEMA_v2 — Speakeater Recipe Schema for Public API Monetization

## Why this doc exists

Recipe submission (v1) is being shipped right now (task #130) with a basic schema. **This doc specifies the v2 enrichment** needed before any external API access is sold. Designed for rate-limited paid API consumers (food-tech startups, meal-planning competitors, cookbook publishers) who need structured, queryable, cookable recipe data.

Authoritative spec for the v2 schema-hardening agent (next task after #130 ships).

---

## Strategic context

Speakeater's recipe corpus = ~24k food + ~4k cocktails. Owned, normalized, photo-rich. Eventual revenue stream: gated `api.speakeater.com/v1/recipes` with tiered pricing (e.g. 100 req/day free, $99/mo for 10k req/day, $999/mo for unlimited). Competitive set: Spoonacular ($0.20/req over free tier), Edamam ($59/mo for 5 req/min), Tasty (Buzzfeed, no public API).

Our pitch vs them: **richer structure** (every step references the ingredients used, every ingredient has a canonical slug + photo, every recipe has provenance + quality signals). Pay extra for queryability.

---

## v2 Recipe schema (target)

### Top-level recipe document

```json
{
  "id": "rcp_abc123",
  "schema_version": "2.0",
  "title": "...",
  "title_normalized": "spaghetti carbonara",   // lowercase, accent-stripped, for search
  "slug": "spaghetti-carbonara-rcp_abc123",     // URL-safe
  "content_type": "food | cocktail | mocktail",
  "cuisine": "italian",                          // canonical from CUISINE_VOCAB
  "cuisine_secondary": ["roman"],                // optional sub-region
  "tags": ["pasta", "egg", "one-pan"],          // canonical free-text tags

  "summary": "Brief 1-2 sentence description.",

  "servings": 4,
  "yield_unit": "servings | pieces | cups | ml | oz",
  "time": {
    "prep_minutes": 10,
    "cook_minutes": 15,
    "total_minutes": 25,
    "rest_minutes": 0
  },
  "difficulty": "easy | medium | hard",
  "skill_required": ["knife_skills", "tempering", "deglazing"],   // canonical skills

  "ingredients": [Ingredient],
  "steps": [Step],
  "equipment": [Equipment],

  "nutrition": {
    "per_serving": {
      "calories": 480,
      "protein_g": 22,
      "carbs_g": 55,
      "fat_g": 18,
      "fiber_g": 3,
      "sugar_g": 4,
      "sodium_mg": 720
    },
    "source": "computed | claimed_by_submitter | unknown"
  },

  "dietary": {
    "vegan": false,
    "vegetarian": false,
    "pescatarian": true,
    "gluten_free": false,
    "dairy_free": false,
    "keto": false,
    "paleo": false,
    "low_fodmap": false,
    "halal": true,
    "kosher": false
  },
  "allergens": ["dairy", "eggs", "wheat"],     // canonical from ALLERGEN_VOCAB

  "photos": [Photo],
  "video_url": "https://...mp4",   // optional

  "provenance": {
    "source": "user_submission | imported_hf | imported_themealdb | imported_wikibooks | imported_cookbook | imported_other",
    "submitted_by_user_id": "usr_xyz" | null,    // null if scraped
    "submitted_at": 1714082400,
    "imported_from_url": "https://..." | null,
    "license": "user_grant | cc-by-sa | cc-by | public_domain | proprietary",
    "attribution_required_text": "...",          // null if not required
    "ai_extracted": true,
    "ai_extraction_confidence": 0.87,             // 0-1, from Claude Vision response
    "manual_edit_count": 3,                       // post-extraction edits
    "schema_org_compatible": true                 // can we emit valid Recipe JSON-LD?
  },

  "quality": {
    "cook_count": 142,
    "save_count": 318,
    "rating_avg": 4.2,
    "rating_n": 28,
    "review_count": 14,
    "deck_surfacing_score": 0.71,                 // internal ranker score, may be exposed in pro tier
    "is_featured": false,
    "is_archived": false                          // failed quality threshold → archived
  },

  "i18n": {
    "primary_lang": "en",
    "translations": {
      "es": { "title": "...", "summary": "..." }   // future
    }
  },

  "api_metadata": {
    "first_published_at": 1714000000,
    "last_modified_at": 1714082400,
    "etag": "W/\"abc123def456\"",
    "deprecated": false
  }
}
```

### Ingredient (rich)

```json
{
  "id": "ing_001",                    // stable per recipe
  "canonical_slug": "tomato",         // from IngredientImage.kt vocab
  "display_name": "ripe Roma tomatoes",
  "quantity": 4,
  "quantity_min": 4,                  // if range "4-6 tomatoes"
  "quantity_max": 6,
  "unit": "whole",                    // from UNIT_VOCAB
  "unit_normalized": "whole",
  "preparation": "diced",             // diced | minced | sliced | grated | ground | etc. — canonical
  "essential": true,                  // false = "optional, see notes"
  "substitutions": [
    { "canonical_slug": "canned_tomato", "ratio": 1.0, "note": "..." }
  ],
  "category": "produce",              // for shopping list aisle grouping
  "allergen_tags": [],
  "notes": "if Roma unavailable, beefsteak works"
}
```

### Step (structured)

```json
{
  "id": "stp_01",
  "order": 1,
  "instruction": "Bring 4 quarts of salted water to a boil.",
  "duration_seconds": 480,
  "temperature": { "value": 212, "unit": "f" } | null,
  "ingredients_used": ["ing_001", "ing_002"],     // refs into ingredients[]
  "equipment_used": ["eqp_001"],                  // refs into equipment[]
  "techniques": ["boil"],                          // canonical from TECHNIQUE_VOCAB
  "image_url": null,                               // optional per-step photo
  "is_critical": true,                             // failure here ruins the dish
  "tip": "Salt should taste like the sea — about 1 tbsp per quart."
}
```

### Equipment

```json
{
  "id": "eqp_001",
  "canonical_slug": "large_pot",          // from EQUIPMENT_VOCAB
  "display_name": "12-quart stockpot",
  "essential": true,
  "alternatives": ["large_pot_alt"]
}
```

### Photo

```json
{
  "id": "pht_001",
  "url": "https://r2.speakeater.com/...jpg",
  "thumbnail_url": "https://r2.speakeater.com/...thumb.jpg",
  "width": 1024,
  "height": 1024,
  "alt_text": "...",                       // accessibility + AI training
  "is_primary": true,
  "ai_generated": true,                    // honest flag
  "license": "speakeater_owned | cc-by | etc.",
  "credit_text": null,                     // "Photo by ..."
  "credit_url": null
}
```

---

## Canonical vocabularies (single source of truth)

These live as JSON files in `backend/data/vocab/` and are used by:
- Recipe submission validation (server)
- Frontend dropdowns (Android)
- Public API request validation
- AI extraction prompts (we tell Claude "use only values from this list")

### `vocab/cuisines.json` (~25 entries)
italian, mexican, japanese, chinese, indian, thai, french, american, mediterranean, korean, vietnamese, middle-eastern, moroccan, spanish, greek, british, caribbean, brazilian, peruvian, ethiopian, turkish, lebanese, filipino, german, southern-us

### `vocab/units.json` (~25 entries)
**Volume:** cup, tbsp, tsp, ml, l, fl-oz, pint, quart, gallon
**Weight:** g, kg, oz, lb
**Count/discrete:** whole, slice, clove, pinch, dash, drop, can, jar, bunch, handful, splash
**Bar-specific (cocktails):** barspoon, jigger, dash, drop

### `vocab/ingredients.json` (~150 canonical slugs)
Source of truth for ingredient names. Currently ~91 slugs in IngredientImage.kt — need to expand to ~150 to cover the recipe corpus comfortably. Each entry:
```json
{
  "slug": "tomato",
  "display_name_default": "tomato",
  "display_aliases": ["tomatoes", "ripe tomato"],
  "category_aisle": "produce",
  "common_units": ["whole", "cup", "oz"],
  "image_url": "https://r2.speakeater.com/ingredients/tomato.jpg",
  "allergen_tags": [],
  "synonyms": ["pomodoro"]
}
```

### `vocab/units.json` (canonical units with conversion factors)
For ingredient quantity normalization across recipes.

### `vocab/equipment.json` (~50 canonical slugs)
sauté_pan, dutch_oven, sheet_pan, blender, food_processor, immersion_blender, mortar_pestle, microplane, mandoline, instant_pot, slow_cooker, sous_vide_immersion_circulator, etc.

### `vocab/techniques.json` (~50 canonical slugs)
boil, simmer, reduce, sear, sauté, roast, grill, broil, deglaze, fold, knead, proof, temper, blanch, brown, caramelize, emulsify, infuse, etc.

### `vocab/skills.json` (~15 canonical slugs)
knife_skills, tempering, emulsifying, kneading, deglazing, reducing, julienning, brunoise, sous_vide, pressure_cooking, etc.

### `vocab/preparation.json` (~30 canonical slugs)
diced, minced, sliced, julienned, brunoised, grated, ground, chopped_fine, chopped_coarse, peeled, cored, pitted, deseeded, halved, quartered, etc.

### `vocab/allergens.json` (FDA top 9 + extensions)
peanuts, tree_nuts, shellfish, fish, eggs, dairy, soy, wheat, sesame, gluten, sulfites, nightshade, etc.

---

## Why this beats the competition

| Feature | Spoonacular | Edamam | Tasty | **Speakeater v2** |
|---|---|---|---|---|
| Step-level ingredient refs | ❌ | ❌ | ❌ | ✅ |
| Step duration / temp / equipment | partial | ❌ | ❌ | ✅ |
| Canonical ingredient + unit vocab | ❌ | partial | ❌ | ✅ |
| Schema.org JSON-LD export | partial | ✅ | ❌ | ✅ |
| Photo provenance + AI flag | ❌ | ❌ | ❌ | ✅ |
| Stable ETags + versioning | ❌ | ❌ | ❌ | ✅ |
| Quality signals (cook count, ratings) | partial | ❌ | ✅ | ✅ |

The big differentiator: **step-level structure**. A consumer who's building a "smart cooking timer" or "voice-guided cooking" app gets per-step duration, equipment, temperature, and which ingredients are used — none of the others give that. Each recipe becomes a programmable workflow, not a blob of prose.

---

## What v1 (task #130) is shipping right now

- Title, cuisine (single-select), content_type, servings, time_minutes, ingredients (canonical_slug + qty + unit), steps (text), submitted_by_user_id, submitted_at
- Validation: server-side from canonical vocab
- Pro-gated submission

That's the foundation. v2 enriches without breaking it.

---

## v2 migration plan (next agent task)

1. **Expand vocabularies** — write the JSON files in `backend/data/vocab/`. Source from existing IngredientImage.kt slugs + audit of corpus.
2. **Add new columns to recipe table** — alter D1 schema to add: `schema_version TEXT`, `tags JSON`, `time_prep_minutes INT`, `time_rest_minutes INT`, `difficulty TEXT`, `skill_required JSON`, `equipment JSON`, `nutrition JSON`, `dietary JSON`, `provenance JSON`, `etag TEXT`, `i18n JSON`. Keep old columns for backward compat.
3. **Add new tables** — `recipe_step_v2` with: `recipe_id`, `step_id`, `order`, `instruction`, `duration_seconds`, `temperature_value`, `temperature_unit`, `ingredients_used JSON`, `equipment_used JSON`, `techniques JSON`, `image_url`, `is_critical BOOL`, `tip`. Keep old `recipe_step` table; migrate gradually.
4. **Backfill existing 24k recipes** — Claude Haiku batch job that reads each old recipe's steps and produces structured v2 steps. Can do incrementally.
5. **Update submission flow (v1 from task #130)** — make it write v2 schema directly for new submissions.
6. **Public API endpoint** — `GET /api/v1/recipes/:id` returns v2 JSON. Auth via API key. Rate limiter via Cloudflare Workers KV. Response includes `schema_version: "2.0"`.
7. **Schema.org JSON-LD export** — `GET /api/v1/recipes/:id?format=jsonld` returns Schema.org-compatible Recipe document for SEO consumers.
8. **API docs site** — `api.speakeater.com/docs` (probably a small Next.js app or static site).
9. **Pricing page + signup** — gates API key creation behind subscription tier.

---

## Pricing targets (rough)

| Tier | Price | Limits | Audience |
|---|---|---|---|
| Free | $0 | 100 req/day | Hobbyist devs, evaluators |
| Hobby | $19/mo | 1k req/day | Solo devs, side projects |
| Startup | $99/mo | 10k req/day, structured search, JSON-LD | Bootstrapped startups |
| Business | $999/mo | unlimited, SLA, priority support | Funded competitors, agencies |

Margin: each request costs ~$0.0001 to serve (D1 + worker compute). Hobby tier @ $19 with 30k req/mo = ~$3 cost = 84% gross margin. Business tier even higher.

---

## Migration log

| Date | Phase | Change | Status |
|---|---|---|---|
| 2026-04-26 | spec | Created this doc | drafted |
| _pending_ | v2-vocab | Write canonical vocab JSONs in backend/data/vocab/ | not started |
| _pending_ | v2-schema | Alter D1 schema for v2 columns + new step table | not started |
| _pending_ | v2-backfill | Claude Haiku batch job to enrich existing 24k recipes | not started |
| _pending_ | v2-submit | Update submission flow to write v2 directly | not started |
| _pending_ | v2-api | Public /api/v1/recipes endpoint + auth + rate limit | not started |
| _pending_ | v2-jsonld | Schema.org JSON-LD export | not started |
| _pending_ | v2-docs | API documentation site | not started |
| _pending_ | v2-pricing | Pricing page + Stripe + key issuance | not started |
