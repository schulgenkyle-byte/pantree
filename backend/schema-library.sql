-- Speakeater Library
-- Three-level hierarchy: Library → Books → Chapters → Recipes
-- Apply additively: wrangler d1 execute pantrie-db-staging --file=schema-library.sql --remote
--
-- Ownership contract:
--   - Every user gets a Library on first access. The Library has 2 STANDARD Books that
--     auto-create and cannot be deleted or renamed: "Saved" and "My Recipes".
--   - Users add custom Books on top (e.g. "Indian", "Cocktails for the Holidays").
--   - Books contain Chapters (accordions). Chapters contain Recipes.
--   - Sharing: Books have visibility (private | unlisted | public). Public Books
--     can be viewed AND forked by anyone. Standard Books are always private.
--
-- Export contract (the strict bit):
--   - Users can only export RECIPES THEY AUTHORED.
--   - Recipe rows now carry created_by_user_id. NULL = Speakeater corpus (NOT exportable).
--     Set on user submissions when they get approved into the recipe table.
--   - Saved/swiped Speakeater corpus recipes are visible inside Books but
--     filtered out at export time. The export endpoint reports counts: how many
--     exported (user-authored) vs how many filtered (corpus).
--   - This is enforced at the SQL layer in the export route, not by trusting clients.

CREATE TABLE IF NOT EXISTS library (
  user_id     TEXT PRIMARY KEY,
  created_at  INTEGER NOT NULL,
  FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS book (
  id               TEXT PRIMARY KEY,
  library_user_id  TEXT NOT NULL,
  slug             TEXT NOT NULL,
  title            TEXT NOT NULL,
  description      TEXT,
  cover_image_url  TEXT,
  visibility       TEXT NOT NULL DEFAULT 'private',  -- 'private' | 'unlisted' | 'public'
  is_standard      INTEGER DEFAULT 0,                -- 1 = system, cannot delete/rename
  standard_kind    TEXT,                             -- 'saved' | 'my_recipes' | NULL
  position         INTEGER DEFAULT 0,
  fork_of_id       TEXT,
  recipe_count     INTEGER DEFAULT 0,                -- denormalized
  view_count       INTEGER DEFAULT 0,
  fork_count       INTEGER DEFAULT 0,
  is_archived      INTEGER DEFAULT 0,
  archived_reason  TEXT,
  created_at       INTEGER NOT NULL,
  updated_at       INTEGER NOT NULL,
  UNIQUE(library_user_id, slug),
  FOREIGN KEY (library_user_id) REFERENCES library(user_id) ON DELETE CASCADE,
  FOREIGN KEY (fork_of_id)      REFERENCES book(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_book_library     ON book(library_user_id, position ASC, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_book_visibility  ON book(visibility, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_book_standard    ON book(library_user_id, standard_kind);

CREATE TABLE IF NOT EXISTS chapter (
  id           TEXT PRIMARY KEY,
  book_id      TEXT NOT NULL,
  title        TEXT NOT NULL,
  position     INTEGER DEFAULT 0,
  recipe_count INTEGER DEFAULT 0,
  created_at   INTEGER NOT NULL,
  FOREIGN KEY (book_id) REFERENCES book(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_chapter_book ON chapter(book_id, position ASC);

CREATE TABLE IF NOT EXISTS chapter_recipe (
  chapter_id  TEXT NOT NULL,
  recipe_id   TEXT NOT NULL,
  position    INTEGER NOT NULL,
  user_note   TEXT,
  added_at    INTEGER NOT NULL,
  PRIMARY KEY (chapter_id, recipe_id),
  FOREIGN KEY (chapter_id) REFERENCES chapter(id) ON DELETE CASCADE,
  FOREIGN KEY (recipe_id)  REFERENCES recipe(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_chapter_recipe_position ON chapter_recipe(chapter_id, position);

CREATE TABLE IF NOT EXISTS book_export_log (
  id                     INTEGER PRIMARY KEY AUTOINCREMENT,
  book_id                TEXT NOT NULL,
  user_id                TEXT NOT NULL,
  format                 TEXT NOT NULL,            -- 'json' | 'markdown'
  recipe_count_exported  INTEGER NOT NULL,         -- user-authored, included in payload
  recipe_count_filtered  INTEGER NOT NULL,         -- corpus + others, omitted
  exported_at            INTEGER NOT NULL,
  FOREIGN KEY (book_id) REFERENCES book(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_book_export_user ON book_export_log(user_id, exported_at DESC);

-- One-shot ALTER: add created_by_user_id to recipe so we can identify which
-- recipes a user authored vs which came from the Speakeater corpus.
-- This statement will FAIL on re-apply (SQLite has no IF NOT EXISTS for columns)
-- but the failure is harmless — column already exists.
-- TODO: backend/src/submissions.js approve flow must populate this column when
-- inserting from recipe_submission into recipe.
ALTER TABLE recipe ADD COLUMN created_by_user_id TEXT;
CREATE INDEX IF NOT EXISTS idx_recipe_creator ON recipe(created_by_user_id);
