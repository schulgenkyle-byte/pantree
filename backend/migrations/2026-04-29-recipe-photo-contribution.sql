-- Recipe photo contributions: a user submits a photo for an EXISTING recipe.
-- If approved by an admin, the photo becomes the recipe's canonical image
-- and the contributor gets photo credit on the card forever.
--
-- This is a separate table from recipe_submission (which is for full recipe
-- submissions). Single responsibility: one row = one photo upload attempt
-- for one existing recipe by one user.
CREATE TABLE IF NOT EXISTS recipe_photo_contribution (
  id              TEXT PRIMARY KEY,
  recipe_id       TEXT NOT NULL,
  user_id         TEXT NOT NULL,
  r2_key          TEXT NOT NULL,         -- contributions/{recipe_id}/{user_id}/{id}.jpg
  image_url       TEXT NOT NULL,         -- public CDN URL
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
