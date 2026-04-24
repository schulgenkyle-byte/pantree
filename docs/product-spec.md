# pan-tree v2 — Pantry-to-Recipes App (Swipe + Review Edition)

_see it. save it. savor it._

> **Hand this to a fresh Claude session with no other context.** Everything needed to build the MVP is in this file. Do not reference v1 of this spec or any other prior project unless explicitly asked.

---

## 0. What this is (30-second pitch)

A mobile-first PWA that solves "what's for dinner" in this exact loop:

1. **Snap** your pantry/fridge → AI identifies every ingredient
2. **Verify** the auto-generated ingredient list
3. **Swipe** through recipe cards (Tinder-style, pantry-matched)
4. **Right-swipe** saves to your Portfolio
5. **Cook** with hands-free Cook Mode (big steps, timers, screen stays on)
6. **Review** your finished dish — upload a photo, rate 1-5 pots, add notes
7. Your reviews build a **public profile** (opt-in) — followers see what you're cooking

The twist: **only people who cooked the dish and uploaded a photo can rate it publicly.** That kills fake reviews and creates a constant stream of authentic user-generated content.

**Working name:** pan-tree

---

## 1. What ships in v1 (MVP scope)

**✅ In scope:**
- Email + Google auth (Supabase)
- 60-second onboarding: allergies, diet, disliked cuisines, skill level
- Pantry photo → vision → editable ingredient list
- Global recipe catalog (seeded from TheMealDB + AI-generated pantry-specific)
- Swipe deck with pantry-match %, undo, dismiss-reason capture
- Portfolio (Saved + Cooked, separate states)
- Cook Mode: full-screen steps, inline timers, screen wake lock, voice-friendly
- "Made it" → photo + 1-5 pots + notes review flow
- Public profile (opt-in) with review feed
- Follow system (friends see what you're cooking)
- Recipe leaderboard (top-rated recipes only, never users)
- Shopping list (auto-populated from missing ingredients + depleted pantry)
- TikTok/Reels share card export after every cook
- Pantry expiration nudges ("chicken expires in 2 days — here are 3 recipes")
- Substitution suggestions via Claude Haiku
- Multi-tenant + RLS on every table from day 1

**❌ Out of scope for v1:**
- Meal planning calendar
- Grocery delivery integration
- Nutrition tracking (available but not surfaced)
- Family/shared pantries
- Payments / premium tier
- Comments on reviews (just pots for now — prevents moderation burden)
- Direct messaging between users
- Native iOS/Android apps
- Recipe forking / remixing

**Target build time:** 8-10 days focused work.

---

## 2. Tech stack (no substitutions)

| Layer | Tech | Notes |
|---|---|---|
| Frontend | Single-file React 18 + Babel standalone in one `index.html` | No build step. Hosted on Cloudflare Pages. |
| State | React `useContext` + hooks | No Redux. |
| Backend | Cloudflare Worker | One worker, route-based. |
| DB | Supabase Postgres + Auth + Storage + RLS | Storage for user review photos. |
| AI (vision + text) | Claude Haiku 4.5 (`claude-haiku-4-5-20251001`) | Pantry scan, recipe gen, substitutions, moderation. |
| AI (images) | Flux.1 schnell via Replicate | ~$0.003/image for AI-generated recipe heroes. Cached in R2. |
| Image storage | Cloudflare R2 | Review photos + generated hero images. Free 10GB/mo. |
| Service Worker | Cache-first shell, network-first data | Standard PWA. |
| Push (later) | Web Push API | For expiration nudges, friend activity. |

**Why this stack:** zero infra, free tier covers ~10K MAU, copy-paste deploys.

---

## 3. Free data strategy (phased)

**Don't pay for recipe data on day 1. You won't need to.**

### Phase 1 — Launch (day 1)
- **TheMealDB bulk import** (~600 recipes, decent images, public domain, no key required). Import once during deploy into `recipes` table with `source='themealdb'`.
- **AI-generated recipes on-demand** for pantry-specific suggestions (Claude Haiku). Each generation creates a persistent recipe row others can see. Hero image generated via Flux.1 schnell (~$0.003), cached in R2.
- **No paid recipe API** until >10K users or validated revenue.

TheMealDB endpoints you'll hit:
- `GET https://www.themealdb.com/api/json/v1/1/categories.php` — categories
- `GET https://www.themealdb.com/api/json/v1/1/filter.php?c=<category>` — list by category
- `GET https://www.themealdb.com/api/json/v1/1/lookup.php?i=<id>` — full recipe + image

### Phase 2 — User-generated content becomes the moat
As users cook + submit reviews with photos, those photos become additional training data. User-submitted recipes (v2) eventually outnumber seeded ones. Their photos replace the AI-generated hero images on recipes they cooked.

### Phase 3 — Only if scaling demands it
Add Spoonacular (paid tier) once AI generation costs exceed $500/mo. Not before.

---

## 4. Multi-tenancy + privacy (non-negotiable)

Every user row is RLS-isolated. Every table has `user_id` where ownership applies. Global recipes are readable by all but writable only by their author.

**Required pattern for user-owned tables:**
```sql
ALTER TABLE <table> ENABLE ROW LEVEL SECURITY;
CREATE POLICY "users see own rows" ON <table>
  FOR ALL USING (user_id = auth.uid())
  WITH CHECK (user_id = auth.uid());
```

**Pattern for global-readable, author-writable tables (recipes, reviews):**
```sql
ALTER TABLE recipes ENABLE ROW LEVEL SECURITY;
CREATE POLICY "anyone can read public recipes" ON recipes
  FOR SELECT USING (is_public = true OR author_user_id = auth.uid());
CREATE POLICY "authors manage their own recipes" ON recipes
  FOR INSERT WITH CHECK (author_user_id = auth.uid());
CREATE POLICY "authors update their own recipes" ON recipes
  FOR UPDATE USING (author_user_id = auth.uid());
```

**Privacy defaults:**
- Profile: PRIVATE by default. Users opt-in to public.
- Reviews: Inherit profile visibility. Private-profile users' reviews don't appear anywhere public.
- Pantry contents: ALWAYS private. Never exposed.
- Allergies/dietary info: ALWAYS private. Never exposed.

Never use `service_role` from the client. Anon key + RLS only.

---

## 5. Database schema

```sql
-- =============================================================
-- USER PROFILES (extends auth.users)
-- =============================================================
CREATE TABLE user_profiles (
  id                    uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  display_name          text NOT NULL,
  avatar_url            text,
  bio                   text,
  is_public             boolean DEFAULT false,
  skill_level           text DEFAULT 'beginner',  -- beginner | intermediate | advanced
  dietary_restrictions  text[] DEFAULT '{}',       -- vegetarian, vegan, keto, halal, etc.
  allergies             text[] DEFAULT '{}',       -- peanuts, shellfish, dairy, gluten, etc.
  disliked_cuisines     text[] DEFAULT '{}',
  disliked_ingredients  text[] DEFAULT '{}',
  current_streak_days   int DEFAULT 0,
  longest_streak_days   int DEFAULT 0,
  last_cooked_date      date,
  total_cooked          int DEFAULT 0,
  created_at            timestamptz DEFAULT now(),
  updated_at            timestamptz DEFAULT now()
);

-- =============================================================
-- PANTRY (per-user, private)
-- =============================================================
CREATE TABLE pantry_items (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  name        text NOT NULL,
  category    text,         -- protein|produce|dairy|grain|pantry|spice|condiment|frozen|beverage|other
  quantity    numeric,
  unit        text,
  expires_at  date,
  photo_url   text,
  added_via   text DEFAULT 'manual',  -- manual | scan
  created_at  timestamptz DEFAULT now(),
  updated_at  timestamptz DEFAULT now()
);
CREATE INDEX ON pantry_items (user_id);
CREATE INDEX ON pantry_items (user_id, expires_at) WHERE expires_at IS NOT NULL;

-- =============================================================
-- GLOBAL RECIPE CATALOG (readable by all, written by authors)
-- =============================================================
CREATE TABLE recipes (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  external_id       text UNIQUE,                    -- for TheMealDB sync (e.g. "themealdb:52772")
  source            text NOT NULL DEFAULT 'ai',     -- themealdb | ai | user
  author_user_id    uuid REFERENCES auth.users(id), -- null for themealdb + system-generated
  title             text NOT NULL,
  description       text,
  hero_image_url    text,
  servings          int DEFAULT 2,
  prep_minutes      int,
  cook_minutes      int,
  ingredients       jsonb NOT NULL,                 -- [{name, quantity, unit}, ...]
  steps             jsonb NOT NULL,                 -- [{order, text, timer_seconds}, ...]
  cuisine           text,
  tags              text[] DEFAULT '{}',
  skill_level       text DEFAULT 'beginner',
  dietary_flags     text[] DEFAULT '{}',            -- vegetarian, vegan, gluten-free, etc.
  allergen_warnings text[] DEFAULT '{}',            -- contains: peanuts, shellfish, etc.
  is_public         boolean DEFAULT true,
  total_ratings     int DEFAULT 0,
  avg_rating        numeric(3,2),                   -- cached for leaderboard perf
  total_cooked      int DEFAULT 0,
  created_at        timestamptz DEFAULT now(),
  updated_at        timestamptz DEFAULT now()
);
CREATE INDEX ON recipes (is_public, avg_rating DESC NULLS LAST);
CREATE INDEX ON recipes (cuisine) WHERE is_public = true;
CREATE INDEX ON recipes USING gin(tags);
CREATE INDEX ON recipes USING gin(dietary_flags);

-- =============================================================
-- USER ↔ RECIPE INTERACTIONS (swipe state + cook state)
-- =============================================================
CREATE TABLE user_recipe_interactions (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id          uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  recipe_id        uuid NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
  status           text NOT NULL,  -- seen | dismissed | saved | cooking | cooked
  dismiss_reason   text,           -- too_complex | ingredient_dislike | wrong_cuisine | boring | allergy | other
  saved_at         timestamptz,
  cook_started_at  timestamptz,
  cooked_at        timestamptz,
  cooked_count     int DEFAULT 0,
  created_at       timestamptz DEFAULT now(),
  updated_at       timestamptz DEFAULT now(),
  UNIQUE(user_id, recipe_id)
);
CREATE INDEX ON user_recipe_interactions (user_id, status);

-- =============================================================
-- REVIEWS (photo + rating + notes) — public-visibility gated
-- =============================================================
CREATE TABLE recipe_reviews (
  id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id            uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  recipe_id          uuid NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
  interaction_id     uuid REFERENCES user_recipe_interactions(id),
  rating_pots        int NOT NULL CHECK (rating_pots BETWEEN 1 AND 5),
  rating_taste       int CHECK (rating_taste BETWEEN 1 AND 5),
  rating_ease        int CHECK (rating_ease BETWEEN 1 AND 5),
  would_make_again   boolean,
  notes              text,
  photo_url          text,               -- required for public visibility
  is_public          boolean DEFAULT false,
  moderation_status  text DEFAULT 'pending',  -- pending | approved | flagged | rejected
  moderation_notes   text,
  created_at         timestamptz DEFAULT now(),
  updated_at         timestamptz DEFAULT now(),
  UNIQUE(user_id, recipe_id)          -- one review per user per recipe; allow updates
);
CREATE INDEX ON recipe_reviews (recipe_id) WHERE is_public = true AND moderation_status = 'approved';
CREATE INDEX ON recipe_reviews (user_id, created_at DESC);

-- =============================================================
-- SOCIAL GRAPH (follows)
-- =============================================================
CREATE TABLE user_follows (
  follower_id  uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  followee_id  uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  created_at   timestamptz DEFAULT now(),
  PRIMARY KEY (follower_id, followee_id),
  CHECK (follower_id <> followee_id)
);
CREATE INDEX ON user_follows (followee_id);

-- =============================================================
-- SHOPPING LIST
-- =============================================================
CREATE TABLE shopping_list (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  name        text NOT NULL,
  quantity    numeric,
  unit        text,
  category    text,
  checked     boolean DEFAULT false,
  added_from  uuid,                     -- nullable ref to recipe_id that added it
  created_at  timestamptz DEFAULT now()
);

-- =============================================================
-- SCAN AUDIT LOG
-- =============================================================
CREATE TABLE scan_history (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id        uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  photo_count    int NOT NULL,
  items_found    int NOT NULL,
  items_added    int NOT NULL,
  cost_estimate  numeric(6,4),
  created_at     timestamptz DEFAULT now()
);

-- =============================================================
-- TRIGGER: maintain avg_rating on recipes after review changes
-- =============================================================
CREATE OR REPLACE FUNCTION refresh_recipe_rating() RETURNS trigger AS $$
BEGIN
  UPDATE recipes r SET
    total_ratings = (SELECT count(*) FROM recipe_reviews WHERE recipe_id = r.id AND is_public = true AND moderation_status = 'approved'),
    avg_rating    = (SELECT avg(rating_pots) FROM recipe_reviews WHERE recipe_id = r.id AND is_public = true AND moderation_status = 'approved')
  WHERE r.id = COALESCE(NEW.recipe_id, OLD.recipe_id);
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER review_changed
AFTER INSERT OR UPDATE OR DELETE ON recipe_reviews
FOR EACH ROW EXECUTE FUNCTION refresh_recipe_rating();
```

Apply RLS policies from section 4 to every table.

---

## 6. Cloudflare Worker API routes

One worker, route-switched by `url.pathname`. Every route verifies Supabase JWT from `Authorization: Bearer <token>`. Return 401 on missing/invalid.

### POST /api/pantry-scan
Vision identification of 1-4 pantry photos.

**Request:**
```json
{
  "images": ["data:image/jpeg;base64,..."],
  "existing_items": ["chicken breast", "olive oil"]
}
```

**Response:**
```json
{
  "ok": true,
  "items": [
    {
      "name": "chicken breast",
      "category": "protein",
      "estimated_quantity": 2,
      "estimated_unit": "lb",
      "confidence": "high",
      "matches_existing": "chicken breast"
    }
  ]
}
```

Client downscales images to 1600px before sending. Handle markdown fences, preamble, and both array/object JSON shapes in the response.

### POST /api/swipe-deck
Returns the next batch of recipe cards scored against the user's pantry + preferences.

**Request:**
```json
{
  "limit": 20,
  "exclude_seen": true
}
```

**Response:**
```json
{
  "ok": true,
  "cards": [
    {
      "recipe_id": "uuid",
      "title": "Lemon Garlic Chicken Skillet",
      "hero_image_url": "https://...",
      "cuisine": "American",
      "skill_level": "beginner",
      "prep_minutes": 10,
      "cook_minutes": 25,
      "uses_pantry_percent": 85,
      "missing_ingredients": [
        { "name": "lemon", "quantity": 1, "unit": "count" }
      ],
      "avg_rating": 4.2,
      "total_ratings": 47,
      "allergen_warnings_match": [],
      "source": "themealdb"
    }
  ]
}
```

**Scoring logic (server-side):**
1. Filter out recipes with allergens in `user_profiles.allergies`
2. Filter out recipes in `user_profiles.disliked_cuisines`
3. Filter out recipes the user has already interacted with (unless `exclude_seen=false`)
4. Score remaining by: `pantry_match_weight * 0.6 + avg_rating_weight * 0.2 + freshness_weight * 0.1 + cuisine_variety_weight * 0.1`
5. If fewer than `limit` recipes qualify, call `/api/generate-recipe-for-pantry` to fill the gap
6. Ensure at least 2 recipes have `uses_pantry_percent >= 75`
7. Ensure cuisine variety (don't return 20 Italian dishes)

### POST /api/swipe-action
Records a swipe decision. Called on every swipe, not batched.

**Request:**
```json
{
  "recipe_id": "uuid",
  "action": "saved" | "dismissed",
  "dismiss_reason": "too_complex" | "ingredient_dislike" | "wrong_cuisine" | "boring" | "allergy" | null
}
```

**Response:**
```json
{ "ok": true, "interaction_id": "uuid" }
```

Prompt client to ask for `dismiss_reason` every ~5th dismissal, not every time. Capture is training data; don't annoy users.

### POST /api/generate-recipe-for-pantry
AI-generates a new recipe optimized for the user's current pantry. Saves it to the global `recipes` table so others can discover it.

**Request:**
```json
{
  "preferences": {
    "servings": 2,
    "max_time_minutes": 45,
    "cuisine_hint": null
  }
}
```

**Response:**
```json
{
  "ok": true,
  "recipe": { /* full recipe row */ }
}
```

**Implementation:**
1. Fetch user's pantry + dietary restrictions + allergies
2. Prompt Claude Haiku with pantry + constraints
3. Insert into `recipes` with `source='ai'`, `author_user_id=NULL`
4. Fire-and-forget: call Flux.1 schnell to generate hero image, update `hero_image_url` when ready

**Prompt template:**
```
You are a practical home cook designing ONE recipe for a specific pantry.

User pantry: <list>
Dietary restrictions: <list>
Allergies: <list>
Servings: <n>
Max total time: <n> minutes
Skill level: <level>

Requirements:
- Use AT LEAST 70% of ingredients from the pantry
- List every missing ingredient the user would need to buy (max 4 missing)
- Mark `have: true` for ingredients from pantry, `have: false` otherwise
- NEVER include any ingredient from the allergy list
- Match skill level — no advanced technique for beginners
- Include timer_seconds on steps that need timing (boiling, baking, resting)
- Flag allergens present (contains peanuts, contains shellfish, etc.)
- Return ONLY valid JSON, no preamble, no markdown fences

Output schema:
{
  "title": "...",
  "description": "...",
  "cuisine": "...",
  "skill_level": "beginner" | "intermediate" | "advanced",
  "dietary_flags": [...],
  "allergen_warnings": [...],
  "servings": <n>,
  "prep_minutes": <n>,
  "cook_minutes": <n>,
  "ingredients": [{"name":"...","quantity":<n>,"unit":"...","have":<bool>}],
  "steps": [{"order":<n>,"text":"...","timer_seconds":<n|null>}]
}
```

### POST /api/cook-start
Marks a saved recipe as being cooked. Starts timestamp.

**Request:** `{ "recipe_id": "uuid" }`
**Response:** `{ "ok": true, "interaction_id": "uuid" }`

### POST /api/cook-complete
Finalizes a cook session. Deducts pantry ingredients. Returns review prompt payload.

**Request:**
```json
{
  "recipe_id": "uuid",
  "consumed": [
    { "pantry_item_id": "uuid", "quantity_used": 1 }
  ]
}
```

**Response:**
```json
{
  "ok": true,
  "depleted_items": ["uuid1"],
  "low_items": ["uuid2"],
  "review_prompt_id": "uuid",
  "share_card_url": "https://..."
}
```

Increments `cooked_count`, updates `last_cooked_date`, bumps streak counter.

### POST /api/submit-review
Uploads a review with photo + pots rating + notes.

**Request:** (multipart/form-data)
```
recipe_id: uuid
rating_pots: 1-5
rating_taste: 1-5 (optional)
rating_ease: 1-5 (optional)
would_make_again: bool (optional)
notes: text (optional, 500 char max)
photo: file (jpeg/png, 10MB max, optional but required for public visibility)
is_public: bool
```

**Response:**
```json
{
  "ok": true,
  "review_id": "uuid",
  "moderation_status": "pending",
  "photo_url": "https://...",
  "share_card_url": "https://..."
}
```

**Flow:**
1. Validate user has `cooked` status for this recipe
2. Upload photo to Supabase Storage (bucket: `review-photos`, path: `user_id/recipe_id.jpg`)
3. If `is_public=true`, run photo through Claude Haiku moderation (see section 14)
4. Insert/update `recipe_reviews` row
5. Generate TikTok share card (see section 17) and return URL
6. Trigger avg_rating refresh via DB trigger

### POST /api/share-card-generate
Creates a vertical 1080x1920 image for TikTok/Reels/Shorts.

**Request:**
```json
{
  "review_id": "uuid",
  "style": "hero" | "before_after" | "rating_card"
}
```

**Response:** `{ "ok": true, "image_url": "https://..." }`

Implementation: serverside HTML → image render via Cloudflare Browser Rendering API, or client-side canvas render uploaded to R2.

### GET /api/profile/:user_id
Public profile page data. Returns 404 if profile is private.

**Response:**
```json
{
  "ok": true,
  "display_name": "...",
  "avatar_url": "...",
  "bio": "...",
  "current_streak_days": 7,
  "total_cooked": 23,
  "reviews": [
    {
      "recipe_id": "uuid",
      "recipe_title": "...",
      "recipe_hero_url": "...",
      "rating_pots": 5,
      "photo_url": "...",
      "notes": "...",
      "created_at": "..."
    }
  ],
  "follower_count": 12,
  "following_count": 8,
  "is_following": false
}
```

### POST /api/follow
**Request:** `{ "followee_id": "uuid", "action": "follow" | "unfollow" }`

### GET /api/feed
Reviews from users the current user follows, newest first.

### GET /api/leaderboard
**Query params:** `?cuisine=italian&skill=beginner&timeframe=week`

**Response:**
```json
{
  "ok": true,
  "recipes": [
    {
      "recipe_id": "uuid",
      "title": "...",
      "hero_image_url": "...",
      "avg_rating": 4.8,
      "total_ratings": 47,
      "cuisine": "italian"
    }
  ]
}
```

Ranks RECIPES only. Never users. Prevents gaming.

### POST /api/substitution
Suggests substitutes for a missing ingredient.

**Request:** `{ "ingredient": "buttermilk", "recipe_id": "uuid (optional context)" }`

**Response:**
```json
{
  "ok": true,
  "substitutions": [
    { "ingredient": "milk + lemon juice", "ratio": "1 cup milk + 1 tbsp lemon juice = 1 cup buttermilk", "confidence": "high" }
  ]
}
```

### POST /api/report
User reports content for moderation.

**Request:** `{ "target_type": "review" | "recipe" | "profile", "target_id": "uuid", "reason": "spam" | "inappropriate" | "allergy_misinfo" | "other", "notes": "text" }`

### POST /api/themealdb-import (admin / cron)
One-shot ingestion of TheMealDB catalog. Run during deploy. Optional monthly refresh.

---

## 7. Onboarding flow (60 seconds, 4 screens)

**Screen 1 — Welcome + auth**
- "Turn your pantry into dinner" tagline
- Google sign-in button (primary)
- Email + magic link option

**Screen 2 — Allergies + dietary** (CRITICAL — never skip)
- Multi-select chips: Peanuts, Tree nuts, Shellfish, Fish, Dairy, Eggs, Gluten, Soy, Sesame
- Diet: None, Vegetarian, Vegan, Pescatarian, Keto, Halal, Kosher
- "None of these" skip option, but clearly marked

**Screen 3 — Cuisines + dislikes**
- "Which cuisines do you love?" multi-select (~12 options)
- "Anything you never want to see?" optional text input (parsed into `disliked_ingredients`)

**Screen 4 — Skill + first scan**
- Slider: 😅 Beginner / 🙂 Intermediate / 😎 Advanced
- "Let's scan your pantry" CTA → camera

Store all in `user_profiles`. Never ask again unless user visits Settings.

---

## 8. Swipe deck mechanics

**Visual:** full-screen card, hero image top 55%, title + cuisine + time + pantry-match % below, skill-level badge, allergen warning badge (only if relevant), "X ratings" pill.

**Gestures:**
- Swipe right → save to portfolio
- Swipe left → dismiss
- Swipe up → open full recipe detail (preview before saving)
- Tap button row: 💔 / ↺ undo / ❤️

**Undo:** 3-second toast after every swipe with "Undo" button. Non-negotiable.

**Dismiss reason capture:**
- First 4 dismissals: no prompt
- 5th dismissal (and every 5th after): bottom sheet "Help us get smarter" with 4 chips: "Too complex", "Don't like an ingredient", "Wrong cuisine", "Just not feeling it"
- Skip button always present

**Deck refill:**
- When ≤ 3 cards remain, fetch next batch of 20 in background
- If user has exhausted catalog that matches their pantry, trigger `/api/generate-recipe-for-pantry` for 3 fresh AI recipes

**Ghost cards / empty state:**
- If pantry < 5 items: "Add more ingredients to get great matches" with scan CTA
- If all filtered out by allergies: "We're out of recipes for your pantry right now — try adding an ingredient"

---

## 9. Cook Mode (hands-free)

**Entry:** From Portfolio → recipe detail → "Cook this" button.

**Features:**
- `navigator.wakeLock.request('screen')` — screen never dims during cook
- Full-screen step view, one step at a time, huge text (24px+)
- Step progress bar at top
- Inline timers with ambient sound when done (vibration + chime)
- "Next step" and "Previous step" large buttons (thumb-reachable)
- Ingredient quick-peek (swipe down from top)
- Servings scaler lives at the top — slider, recalculates all quantities live
- Substitution tap on any ingredient → calls `/api/substitution`
- Voice control (v2): "Hey pan-tree, next step" — use Web Speech API

**Exit:** "I'm done cooking" button → `/api/cook-complete` → review flow.

---

## 10. Review submission flow (photo + pots + notes)

This is the heart of the UGC loop. Make it frictionless.

**Trigger:** Immediately after "I'm done cooking" in Cook Mode.

**Screen 1 — Photo**
- Big camera button with "Snap your finished dish" prompt
- "Skip photo" link (small, grey) — but warns: "Photo required for public rating"
- Crop/rotate simple controls

**Screen 2 — Rate**
- 5 pot icons (🍳 or custom pot SVG), tap to rate 1-5
- Optional: "How was the taste?" / "How easy was it?" mini sub-ratings (can skip)
- Optional: "Would you make it again?" yes/no chips

**Screen 3 — Notes + publish**
- Multi-line text field, 500 char max, placeholder: "Tips for next time? Substitutions? Anything worth remembering?"
- Toggle: "Share publicly" (default OFF if profile is private, default ON if public)
- "Submit" button

**Screen 4 — Share**
- Auto-generated TikTok share card preview
- Buttons: "Share to TikTok", "Share to Instagram", "Copy link", "Skip"

**Persistence:** If user closes mid-flow, save draft. Show "Finish your review" card on home screen.

**Edit later:** Portfolio → Cooked tab → any recipe → "Edit review".

---

## 11. Public profile / social layer

**Profile page (public):**
- Avatar, display name, bio, streak badge, total cooked count, follower/following counts
- Review grid (3 columns, like Instagram) — each tile is the review photo
- Tap tile → review detail sheet (photo, recipe link, pots, notes)
- Follow / unfollow button

**Profile page (private user):**
- 404 equivalent: "This profile is private"

**Feed (followed users):**
- Chronological review stream, no algorithm
- Each card: avatar, display name, "cooked [recipe title]", photo, pots, notes preview
- Tap → review detail sheet
- Tap recipe link → recipe detail → can save to own portfolio

**Leaderboard:**
- Global or filtered (cuisine / skill level / timeframe)
- Top 20 RECIPES by avg pot rating (min 10 ratings to appear — prevents gaming with one 5-star)
- Each card: title, hero, avg rating, total cooks

**Never rank users.** Removes the toxic "foodie influencer" dynamic. If users want clout, they get it via follower count organically.

---

## 12. Moderation + safety

**Photo moderation (required before going public):**
- Every review photo goes through Claude Haiku with this prompt:
```
You are moderating a photo submitted as a "finished dish" review for a recipe app.
Classify into: APPROVED (food visible, appropriate) | FLAGGED (possibly inappropriate, needs human review) | REJECTED (clearly inappropriate — nudity, violence, non-food, hate imagery).
Return JSON: {"classification": "...", "reason": "..."}
```
- APPROVED → `moderation_status='approved'`, visible immediately
- FLAGGED → `moderation_status='pending'`, hidden from public, emailed to admin queue
- REJECTED → `moderation_status='rejected'`, hidden permanently, notify user

**Text moderation (notes field):**
- Same Claude Haiku call for `notes` text. Check for hate/harassment/PII.

**Report flow:**
- Report button on every public review + profile
- Reports insert into admin-only table, auto-hide after 3 reports pending review

**User blocking:**
- Block button on profile → creates row in `user_blocks`
- Feed + leaderboard queries filter blocked users
- v2 feature, but schema should support it

**Allergy misinformation:**
- Special report category. High-priority queue.
- Never allow users to strip `allergen_warnings` from AI/TheMealDB recipes without human review

---

## 13. TikTok / Reels share card generator

Kyle has TikTok distribution — this is the growth loop.

**Templates (3 styles):**
1. **Hero:** Full-bleed photo + "I made [Recipe Name]" + pot rating overlay + pantrie.app URL
2. **Before/after:** Left half: pantry photo, Right half: finished dish, + pots + link
3. **Rating card:** Stylized card with recipe name, pots, and 3-word review snippet

**Generation:**
- Canvas-based (client-side for speed): composite photo + text + logo watermark
- 1080×1920 PNG
- Auto-download + share sheet trigger (`navigator.share` on mobile)

**Attribution:**
- Watermark: subtle "made with pan-tree" in corner
- Auto-appends hashtags: #pantree #whatsfordinner + recipe cuisine hashtag

**v2:** Pre-generated short video templates (Ken Burns zoom on photo + text overlay + 3s music bed). Huge for TikTok engagement.

---

## 14. UX principles

- **2-tap rule** for core actions
- **Mobile-only.** Tap targets ≥ 44px. No hover states.
- **Thumb-zone primary actions.** Bottom tab bar + primary CTAs in lower third.
- **Offline-tolerant.** SW caches shell; data syncs when online.
- **No dark patterns.** No rate-the-app popups, no forced tours.
- **Generous undo.** Every destructive action = 3-5s undo toast.
- **Empty states teach.** First-time user opens Pantry → warm illustration + "📸 Scan your pantry to get started"
- **Copy is casual.** "What's in your pantry?" not "Add your first inventory item."
- **Emoji-forward** in categories (🫙 🥕 🍳 🥘 🛒)
- **Streak flex** prominent on home — dopamine loop
- **Skill-sensitive.** Beginners never see "butcher your own fish."

---

## 15. Cost projection

| Action | Cost | Notes |
|---|---|---|
| Pantry scan (4 imgs) | ~$0.02 | Haiku vision |
| AI recipe generation | ~$0.006 | Haiku text |
| Flux.1 hero image (cached per recipe) | ~$0.003 | One-time per AI recipe |
| Photo moderation | ~$0.002 | Haiku vision on review photo |
| Text moderation | ~$0.0005 | Haiku text on notes |
| Substitution query | ~$0.001 | Haiku text |

**Typical active user / month:**
- 2 pantry scans, 8 swipe decks (recipes pre-scored, no AI), 3 cook completions, 3 photo moderations, 5 substitution queries ≈ **$0.06/user/month**

At 1K users: ~$60/mo Anthropic + Replicate
At 10K users: ~$600/mo
At 100K users: ~$6K/mo — easily supported by freemium or affiliate

Supabase free tier = 500MB DB + 1GB storage → covers ~5K MAU before first paid tier ($25/mo).
Cloudflare R2 free tier = 10GB stored, 10M ops/mo → covers ~20K MAU.

---

## 16. Brand direction

- **Vibe:** Warm, quirky, not corporate
- **Colors:** Cream #FAF6EF background, tomato-red #E64A2E primary, sage-green #7FA88F secondary, dark ink #2B2B2B text
- **Typography:** Inter for UI, Fraunces (display) for recipe titles only
- **Pot icon:** Custom SVG, filled/unfilled states for rating
- **Photography:** Always rounded corners, soft shadow, never hard rectangles
- **Motion:** Spring-based swipe with slight rotation (10-15°), not linear drag

---

## 17. Deliverables / build order

Build in this exact order:

1. **Supabase setup** — new project, Auth (email + Google), Storage buckets (`review-photos`, `recipe-images`, `pantry-scans`), SQL migration file `migrations/001-init.sql` with all tables + RLS + triggers
2. **Cloudflare Worker (`api/index.js`)** — JWT verification helper, all routes from section 6, CORS preflight, robust JSON extraction
3. **TheMealDB importer** — standalone script `scripts/import-themealdb.js` that populates `recipes` table. Run once during initial deploy.
4. **PWA (`public/index.html`, `public/sw.js`, `public/manifest.json`)** — single-file React + Babel, all 4 tabs + swipe deck + Cook Mode + review flow
5. **Share card generator** — client-side canvas implementation for 3 templates
6. **Deployment docs (`README.md`)** — Supabase setup walkthrough, Worker env vars (`ANTHROPIC_API_KEY`, `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY`, `REPLICATE_API_TOKEN`, `R2_ACCESS_KEY`, `R2_SECRET_KEY`), Cloudflare Pages deploy, domain config
7. **Sample seed data (`migrations/002-sample.sql`)** — one test user + populated pantry for dev without scanning

---

## 18. Reference code patterns

**Vision call pattern (use verbatim):**
```javascript
const anthropicRes = await fetch('https://api.anthropic.com/v1/messages', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'x-api-key': env.ANTHROPIC_API_KEY,
    'anthropic-version': '2023-06-01',
  },
  body: JSON.stringify({
    model: 'claude-haiku-4-5-20251001',
    max_tokens: 2500,
    messages: [{
      role: 'user',
      content: [
        ...images.map(dataUrl => {
          const m = dataUrl.match(/^data:(image\/[^;]+);base64,(.+)$/);
          return { type: 'image', source: { type: 'base64', media_type: m[1], data: m[2] } };
        }),
        { type: 'text', text: prompt },
      ],
    }],
  }),
});
```

**Robust JSON extraction:**
```javascript
function extractJSON(text) {
  const cleaned = text.replace(/^```(?:json)?\s*/i, '').replace(/\s*```\s*$/, '').trim();
  // Try direct parse first
  try { return JSON.parse(cleaned); } catch {}
  // Find outermost { or [ and matching closer
  const start = cleaned.search(/[\[{]/);
  if (start === -1) throw new Error('no JSON found');
  const opener = cleaned[start];
  const closer = opener === '{' ? '}' : ']';
  let depth = 0, end = -1;
  for (let i = start; i < cleaned.length; i++) {
    if (cleaned[i] === opener) depth++;
    if (cleaned[i] === closer) { depth--; if (depth === 0) { end = i; break; } }
  }
  return JSON.parse(cleaned.slice(start, end + 1));
}
```

**Client-side image downscale:**
```javascript
async function downscale(file, maxDim = 1600) {
  const img = await createImageBitmap(file);
  const ratio = Math.min(1, maxDim / Math.max(img.width, img.height));
  const w = Math.round(img.width * ratio), h = Math.round(img.height * ratio);
  const canvas = new OffscreenCanvas(w, h);
  canvas.getContext('2d').drawImage(img, 0, 0, w, h);
  const blob = await canvas.convertToBlob({ type: 'image/jpeg', quality: 0.82 });
  return await new Promise(res => {
    const r = new FileReader();
    r.onload = () => res(r.result);
    r.readAsDataURL(blob);
  });
}
```

**Swipe gesture (pure React, no library):**
Use `useRef` + `pointerdown/move/up`, track `deltaX`, apply `transform: translateX + rotate(deltaX/20deg)` in real time. Commit swipe when `|deltaX| > 120px` OR velocity > 0.5px/ms.

**Screen wake lock in Cook Mode:**
```javascript
let wakeLock = null;
async function enterCookMode() {
  try { wakeLock = await navigator.wakeLock.request('screen'); } catch (e) {}
}
async function exitCookMode() {
  if (wakeLock) { await wakeLock.release(); wakeLock = null; }
}
document.addEventListener('visibilitychange', () => {
  if (wakeLock && document.visibilityState === 'visible') enterCookMode();
});
```

---

## 19. Success criteria

The MVP is "done" when all of these are true:

- [ ] New user completes signup + onboarding in under 90 seconds
- [ ] One pantry photo yields ≥ 5 correctly identified ingredients
- [ ] Swipe deck loads 20 cards in < 2s, next batch prefetches invisibly
- [ ] Undo after swipe works within 3-second window
- [ ] Cook Mode keeps screen awake across full cook session (tested 20+ min)
- [ ] "Made it" → photo upload + rating + notes completes in < 30s
- [ ] Review with photo appears on public profile within 5s (moderation passes)
- [ ] Recipe `avg_rating` updates within 5s of new review via trigger
- [ ] Share card exports as 1080×1920 PNG with photo + title + pots
- [ ] TheMealDB catalog fully imported (600+ recipes with images)
- [ ] Allergy filter 100% excludes flagged recipes (validated with 3 test cases)
- [ ] User A's pantry, reviews, follows, etc. NEVER visible to User B unless opted-in
- [ ] Moderation correctly flags inappropriate test photo
- [ ] PWA installable on iOS Safari + Android Chrome
- [ ] End-to-end cost at 1K users < $100/mo

---

## 20. Things to explicitly NOT do

- **Do not** skip allergy onboarding. Safety-critical.
- **Do not** allow public rating without photo proof of cook.
- **Do not** rank users publicly. Only recipes.
- **Do not** use `service_role` from the client.
- **Do not** pay for Spoonacular/Edamam/etc. on day 1. TheMealDB + AI generation covers it.
- **Do not** build native apps. PWA only.
- **Do not** add comments on reviews in v1. Moderation cost too high.
- **Do not** build a grocery delivery integration. Link out only.
- **Do not** nutrition-track in v1. Data exists, don't surface.
- **Do not** over-engineer. Any feature > 4h → defer to v2.
- **Do not** require email verification. Friction kills signup conversion.

---

## 21. Open questions for builder

1. **Name confirmed as pan-tree?** Also consider: Simmer, Panful, Plait, Stockpile
2. **Domain:** check `pantrie.app` (likely taken), `getpantrie.com`, `trypantrie.com`
3. **Auth:** Email magic link MVP, add Google OAuth once Cloudflare side configured
4. **First 10 testers:** owner's TikTok audience (built-in distribution advantage)
5. **Pot icon design:** default to custom SVG or use emoji 🍲 on launch?
6. **Moderation admin dashboard:** v1 = just a Supabase SQL query; v2 = proper UI

---

*End of spec. Build with care, ship fast, iterate on real user feedback. TikTok distribution is the launch vector — deploy-ability is the highest priority.*
