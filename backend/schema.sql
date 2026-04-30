-- Pantrie D1 schema v2 (hardened).
-- Run fresh:   wrangler d1 execute pantrie-db --file=schema.sql --remote
-- Notes: D1 / SQLite FK enforcement requires PRAGMA per-connection; handlers still scope by user_id defensively.

PRAGMA foreign_keys = ON;

-- ---------- USERS ----------
CREATE TABLE IF NOT EXISTS user (
  id            TEXT PRIMARY KEY,
  google_sub    TEXT UNIQUE,
  email         TEXT,
  email_hash    TEXT,
  display_name  TEXT,
  bio           TEXT,
  diet          TEXT DEFAULT 'None',
  skill_level   TEXT DEFAULT 'intermediate',
  created_at    INTEGER NOT NULL,
  updated_at    INTEGER
);
CREATE INDEX IF NOT EXISTS idx_user_google     ON user(google_sub);
CREATE INDEX IF NOT EXISTS idx_user_email_hash ON user(email_hash);

CREATE TABLE IF NOT EXISTS user_allergy (
  user_id   TEXT NOT NULL,
  allergen  TEXT NOT NULL,
  PRIMARY KEY (user_id, allergen),
  FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);

-- ---------- SESSIONS ----------
-- token_hash = SHA-256 hex of the opaque refresh token. Token itself never hits disk.
-- family_id groups rotations of the same login so we can invalidate on reuse detection.
CREATE TABLE IF NOT EXISTS session (
  token_hash      TEXT PRIMARY KEY,
  user_id         TEXT NOT NULL,
  family_id       TEXT NOT NULL,
  rotation_count  INTEGER NOT NULL DEFAULT 0,
  created_at      INTEGER NOT NULL,
  expires_at      INTEGER NOT NULL,
  family_root_at  INTEGER NOT NULL,
  FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_session_user   ON session(user_id);
CREATE INDEX IF NOT EXISTS idx_session_family ON session(family_id);

-- ---------- PANTRY ----------
CREATE TABLE IF NOT EXISTS pantry_item (
  id                  TEXT PRIMARY KEY,
  user_id             TEXT NOT NULL,
  name                TEXT NOT NULL,
  canonical_name      TEXT,              -- synonym-canonicalized form for matching
  category            TEXT,
  quantity            REAL,
  unit                TEXT,
  expires_at          TEXT,
  original_shelf_days INTEGER,           -- shelf life at insert; >180 suppresses 'Expiring' badge
  created_at          INTEGER NOT NULL,
  FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_pantry_user ON pantry_item(user_id);
CREATE INDEX IF NOT EXISTS idx_pantry_canonical ON pantry_item(canonical_name);

-- ---------- RECIPES (public catalog) ----------
CREATE TABLE IF NOT EXISTS recipe (
  id                TEXT PRIMARY KEY,
  title             TEXT NOT NULL,
  cuisine           TEXT,
  description       TEXT,
  skill_level       TEXT,
  prep_minutes      INTEGER,
  cook_minutes      INTEGER,
  servings          INTEGER,
  avg_rating        REAL DEFAULT 0,
  total_ratings     INTEGER DEFAULT 0,
  cook_count        INTEGER DEFAULT 0,  -- social proof: how many users cooked this recipe
  dietary_flags     TEXT,
  allergen_warnings TEXT,
  nutrition         TEXT,  -- JSON blob: { calories, protein_g, carbs_g, fat_g, fiber_g, sodium_mg, confidence }
  image_url         TEXT,  -- public image URL (TheMealDB thumbs, user-submitted after review, etc.)
  photo_credit      TEXT,  -- "Alpha" — required attribution text when photo license requires it (CC-BY-SA etc.)
  photo_license     TEXT,  -- "CC BY-SA 4.0" — license short name
  photo_source_url  TEXT,  -- link back to Wikimedia Commons File: page or source URL
  attribution       TEXT   -- freeform attribution for the whole recipe ("Source: Health Canada · Open Government Licence")
);
CREATE INDEX IF NOT EXISTS idx_recipe_cook_count ON recipe(cook_count DESC);
CREATE INDEX IF NOT EXISTS idx_recipe_has_photo ON recipe(id) WHERE image_url IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_recipe_popularity ON recipe(cook_count DESC, avg_rating DESC);

CREATE TABLE IF NOT EXISTS recipe_ingredient (
  recipe_id       TEXT NOT NULL,
  seq             INTEGER NOT NULL,
  name            TEXT NOT NULL,
  canonical_name  TEXT,              -- synonym-canonicalized form for matching
  quantity        REAL,
  unit            TEXT,
  aisle           TEXT,
  subs            TEXT,
  PRIMARY KEY (recipe_id, seq),
  FOREIGN KEY (recipe_id) REFERENCES recipe(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_recipe_ingredient_canonical ON recipe_ingredient(canonical_name);

CREATE TABLE IF NOT EXISTS recipe_step (
  recipe_id     TEXT NOT NULL,
  seq           INTEGER NOT NULL,
  text          TEXT NOT NULL,
  timer_seconds INTEGER,
  PRIMARY KEY (recipe_id, seq),
  FOREIGN KEY (recipe_id) REFERENCES recipe(id) ON DELETE CASCADE
);

-- ---------- INTERACTIONS ----------
CREATE TABLE IF NOT EXISTS interaction (
  id             TEXT PRIMARY KEY,
  user_id        TEXT NOT NULL,
  recipe_id      TEXT NOT NULL,
  status         TEXT NOT NULL,
  dismiss_reason TEXT,
  created_at     INTEGER NOT NULL,
  UNIQUE(user_id, recipe_id),
  FOREIGN KEY (user_id)   REFERENCES user(id)   ON DELETE CASCADE,
  FOREIGN KEY (recipe_id) REFERENCES recipe(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_interaction_user ON interaction(user_id);
CREATE INDEX IF NOT EXISTS idx_interaction_user_status_time ON interaction(user_id, status, created_at);

-- ---------- REVIEWS ----------
CREATE TABLE IF NOT EXISTS review (
  id            TEXT PRIMARY KEY,
  user_id       TEXT NOT NULL,
  recipe_id     TEXT NOT NULL,
  rating_pots   INTEGER NOT NULL CHECK (rating_pots BETWEEN 1 AND 5),
  rating_taste  INTEGER CHECK (rating_taste BETWEEN 1 AND 5),
  rating_ease   INTEGER CHECK (rating_ease  BETWEEN 1 AND 5),
  notes         TEXT,
  cook_again    INTEGER,
  is_public     INTEGER DEFAULT 0,
  photo_url     TEXT,
  moderation    TEXT DEFAULT 'approved',
  created_at    INTEGER NOT NULL,
  UNIQUE(user_id, recipe_id),
  FOREIGN KEY (user_id)   REFERENCES user(id)   ON DELETE CASCADE,
  FOREIGN KEY (recipe_id) REFERENCES recipe(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_review_user   ON review(user_id);
CREATE INDEX IF NOT EXISTS idx_review_recipe ON review(recipe_id);
CREATE INDEX IF NOT EXISTS idx_review_public ON review(is_public, moderation, created_at);

CREATE TABLE IF NOT EXISTS review_photo (
  id          TEXT PRIMARY KEY,
  user_id     TEXT NOT NULL,
  review_id   TEXT NOT NULL,
  url         TEXT NOT NULL,
  created_at  INTEGER NOT NULL,
  FOREIGN KEY (user_id)   REFERENCES user(id)     ON DELETE CASCADE,
  FOREIGN KEY (review_id) REFERENCES review(id)   ON DELETE CASCADE
);

-- ---------- PLANS ----------
CREATE TABLE IF NOT EXISTS plan (
  id          TEXT PRIMARY KEY,
  user_id     TEXT NOT NULL,
  name        TEXT,
  recipe_ids  TEXT NOT NULL,
  created_at  INTEGER NOT NULL,
  FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_plan_user ON plan(user_id);

-- ---------- SHOPPING ----------
CREATE TABLE IF NOT EXISTS shopping_item (
  id              TEXT PRIMARY KEY,
  user_id         TEXT NOT NULL,
  name            TEXT NOT NULL,
  quantity        REAL,
  unit            TEXT,
  aisle           TEXT,
  checked         INTEGER DEFAULT 0,
  source          TEXT DEFAULT 'manual',
  source_plan_id  TEXT,
  created_at      INTEGER NOT NULL,
  FOREIGN KEY (user_id)        REFERENCES user(id) ON DELETE CASCADE,
  FOREIGN KEY (source_plan_id) REFERENCES plan(id) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_shopping_user ON shopping_item(user_id);
CREATE INDEX IF NOT EXISTS idx_shopping_plan ON shopping_item(source_plan_id);

-- ---------- SOCIAL ----------
CREATE TABLE IF NOT EXISTS follow (
  id                TEXT PRIMARY KEY,
  user_id           TEXT NOT NULL,
  followed_user_id  TEXT NOT NULL,
  created_at        INTEGER NOT NULL,
  UNIQUE(user_id, followed_user_id),
  FOREIGN KEY (user_id)          REFERENCES user(id) ON DELETE CASCADE,
  FOREIGN KEY (followed_user_id) REFERENCES user(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_follow_user     ON follow(user_id);
CREATE INDEX IF NOT EXISTS idx_follow_followed ON follow(followed_user_id);

CREATE TABLE IF NOT EXISTS block (
  id                TEXT PRIMARY KEY,
  user_id           TEXT NOT NULL,
  blocked_user_id   TEXT NOT NULL,
  created_at        INTEGER NOT NULL,
  UNIQUE(user_id, blocked_user_id),
  FOREIGN KEY (user_id)         REFERENCES user(id) ON DELETE CASCADE,
  FOREIGN KEY (blocked_user_id) REFERENCES user(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_block_user    ON block(user_id);
CREATE INDEX IF NOT EXISTS idx_block_blocked ON block(blocked_user_id);

-- ---------- SCAN AUDIT ----------
CREATE TABLE IF NOT EXISTS scan_history (
  id          TEXT PRIMARY KEY,
  user_id     TEXT NOT NULL,
  item_count  INTEGER,
  created_at  INTEGER NOT NULL,
  FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_scan_user ON scan_history(user_id);
CREATE INDEX IF NOT EXISTS idx_scan_history_user_time ON scan_history(user_id, created_at);

-- ---------- BILLING ----------
-- purchase_token_hash is UNIQUE to prevent reuse of the same receipt across accounts.
CREATE TABLE IF NOT EXISTS entitlement (
  user_id              TEXT PRIMARY KEY,
  sku                  TEXT NOT NULL,
  purchase_token_hash  TEXT UNIQUE,
  expires_at           INTEGER NOT NULL,
  auto_renewing        INTEGER DEFAULT 0,
  updated_at           INTEGER NOT NULL,
  FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_entitlement_token ON entitlement(purchase_token_hash);
CREATE INDEX IF NOT EXISTS idx_entitlement_exp   ON entitlement(expires_at);

-- ---------- COOK UNDO SNAPSHOTS ----------
-- When a user marks a recipe cooked we deduct pantry quantities. This table stores
-- the inverse so the user can undo an accidental tap within UNDO_WINDOW_MS.
CREATE TABLE IF NOT EXISTS cook_undo (
  id            TEXT PRIMARY KEY,
  user_id       TEXT NOT NULL,
  recipe_id     TEXT NOT NULL,
  deductions    TEXT NOT NULL,      -- JSON: [{pantry_id, qtyBefore, qtyAfter}]
  created_at    INTEGER NOT NULL,
  FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_cook_undo_user_created ON cook_undo(user_id, created_at);

-- ---------- CORE INGREDIENTS (pantry history) ----------
-- For each user, stamp one row per (ingredient, week) when we see it in their pantry.
-- Appearing in ≥3 of the last 6 weeks marks the ingredient CORE.
CREATE TABLE IF NOT EXISTS pantry_week_seen (
  user_id     TEXT NOT NULL,
  ingredient  TEXT NOT NULL,   -- lowercased normalized name
  week_id     INTEGER NOT NULL, -- floor(Date.now() / 604800000)
  created_at  INTEGER NOT NULL,
  PRIMARY KEY (user_id, ingredient, week_id),
  FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_pantry_week_seen_user ON pantry_week_seen(user_id, week_id);

-- ---------- WASTE / SAVINGS ----------
CREATE TABLE IF NOT EXISTS waste_event (
  id             TEXT PRIMARY KEY,
  user_id        TEXT NOT NULL,
  item_id        TEXT,         -- optional FK to pantry_item (may be deleted by the time we read)
  name           TEXT NOT NULL,
  category       TEXT NOT NULL,
  quantity       REAL,
  unit           TEXT,
  action         TEXT NOT NULL CHECK (action IN ('cooked','consumed','donated','wasted','expired')),
  est_cost_usd   REAL DEFAULT 0,
  created_at     INTEGER NOT NULL,
  FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_waste_user_created ON waste_event(user_id, created_at);

-- ---------- RECIPE SUBMISSIONS (user-generated catalog) ----------
CREATE TABLE IF NOT EXISTS recipe_submission (
  id                TEXT PRIMARY KEY,
  user_id           TEXT NOT NULL,
  title             TEXT NOT NULL,
  cuisine           TEXT,
  description       TEXT,
  prep_minutes      INTEGER,
  cook_minutes      INTEGER,
  servings          INTEGER,
  ingredients_json  TEXT NOT NULL,     -- [{name, quantity, unit, aisle}]
  steps_json        TEXT NOT NULL,     -- [{text, timer_seconds}]
  image_url         TEXT NOT NULL,     -- R2 URL or signed public URL (required at submit time)
  status            TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','approved','rejected','duplicate')),
  reject_reason     TEXT,
  dup_of_recipe_id  TEXT,
  approved_as       TEXT,              -- recipe.id assigned on approval
  created_at        INTEGER NOT NULL,
  reviewed_at       INTEGER,
  FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_sub_status ON recipe_submission(status, created_at);
CREATE INDEX IF NOT EXISTS idx_sub_user ON recipe_submission(user_id, created_at);

-- Recipe photo contributions: user submits a photo for an EXISTING recipe.
-- If approved, the photo becomes the recipe's canonical image and the
-- contributor gets photo credit on the card forever.
CREATE TABLE IF NOT EXISTS recipe_photo_contribution (
  id              TEXT PRIMARY KEY,
  recipe_id       TEXT NOT NULL,
  user_id         TEXT NOT NULL,
  r2_key          TEXT NOT NULL,
  image_url       TEXT NOT NULL,
  status          TEXT NOT NULL DEFAULT 'pending'
                  CHECK (status IN ('pending','approved','rejected','superseded')),
  reject_reason   TEXT,
  reviewed_by     TEXT,
  reviewed_at     INTEGER,
  created_at      INTEGER NOT NULL,
  FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
  FOREIGN KEY (recipe_id) REFERENCES recipe(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_recipe_photo_contrib_status
  ON recipe_photo_contribution(status, created_at);
CREATE INDEX IF NOT EXISTS idx_recipe_photo_contrib_recipe
  ON recipe_photo_contribution(recipe_id, created_at);
CREATE INDEX IF NOT EXISTS idx_recipe_photo_contrib_user
  ON recipe_photo_contribution(user_id, created_at);

-- ---------- BETA FEEDBACK ----------
CREATE TABLE IF NOT EXISTS beta_feedback (
  id           TEXT PRIMARY KEY,
  user_id      TEXT,
  kind         TEXT NOT NULL CHECK (kind IN ('bug','idea','praise','other')),
  title        TEXT NOT NULL,
  body         TEXT,
  route        TEXT,
  app_version  TEXT,
  device       TEXT,
  logs         TEXT,
  severity     TEXT,
  status       TEXT DEFAULT 'open',
  created_at   INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_beta_feedback_created ON beta_feedback(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_beta_feedback_kind    ON beta_feedback(kind, status);

-- ---------- ANALYTICS EVENTS ----------
-- Lightweight event log. Props is a JSON blob; keep small (<2KB per event).
CREATE TABLE IF NOT EXISTS event (
  id          TEXT PRIMARY KEY,
  user_id     TEXT,
  name        TEXT NOT NULL,
  props       TEXT,
  route       TEXT,
  session_id  TEXT,
  app_version TEXT,
  ts          INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_event_name_ts ON event(name, ts);
CREATE INDEX IF NOT EXISTS idx_event_user_ts ON event(user_id, ts);

-- ---------- MODERATION ----------
CREATE TABLE IF NOT EXISTS report (
  id           TEXT PRIMARY KEY,
  user_id      TEXT NOT NULL,
  target_type  TEXT NOT NULL,
  target_id    TEXT NOT NULL,
  reason       TEXT,
  created_at   INTEGER NOT NULL,
  UNIQUE(user_id, target_type, target_id),
  FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_report_target ON report(target_type, target_id);

-- ---------- USER PREFERENCES (onboarding quiz + palate) ----------
-- One row per user. Taste profile is computed live from interactions + reviews (no table).
CREATE TABLE IF NOT EXISTS user_preferences (
  user_id         TEXT PRIMARY KEY,
  cuisines_json   TEXT,                  -- JSON string array of preferred cuisines
  avoid_json      TEXT,                  -- JSON string array of ingredient names to avoid
  diet            TEXT DEFAULT 'none',   -- none|vegetarian|vegan|pescatarian|keto|paleo|gluten-free|dairy-free
  allergens_json  TEXT,                  -- JSON string array of allergens
  heat            INTEGER DEFAULT 1,     -- 0 (none) .. 3 (fiery)
  adventure       INTEGER DEFAULT 1,     -- 0 (stick to faves) .. 3 (surprise me)
  onboarded       INTEGER DEFAULT 0,     -- 1 once the first-run quiz is complete
  updated_at      INTEGER,
  FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);

