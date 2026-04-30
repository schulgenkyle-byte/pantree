-- Migration: link-import infrastructure.
-- Apply with:
--   wrangler d1 execute pantrie-db-staging --file=schema-import.sql --remote
--
-- Adds:
--   1. recipe_submission.source column — distinguishes link-imported submissions
--      from native photo-based submissions for analytics + admin filtering.
--   2. import_job table — one row per /api/import/links call. Tracks fan-out
--      status across up to 10 link_jobs.
--   3. link_job table — one row per URL inside an import_job. Stores the parsed
--      recipe envelope (or error) so the Android review screen can render it.
--      We DO NOT write to recipe_submission until the user confirms in the
--      review screen — link_jobs are the staging area for unconfirmed imports.

PRAGMA foreign_keys = ON;

-- ---------- recipe_submission.source ----------
-- Existing rows default to 'photo' (the only previous flow). New flows:
--   'photo'        — original /submissions/recipe with R2-uploaded photo
--   'photo-extract'— /me/extract-recipe-from-photo Pro flow
--   'link-import'  — POST /api/import/links Pro flow
ALTER TABLE recipe_submission ADD COLUMN source TEXT DEFAULT 'photo';
ALTER TABLE recipe_submission ADD COLUMN source_url TEXT;     -- for link-import: the original TikTok/YT URL

-- ---------- import_job ----------
CREATE TABLE IF NOT EXISTS import_job (
  id            TEXT PRIMARY KEY,
  user_id       TEXT NOT NULL,
  status        TEXT NOT NULL DEFAULT 'queued' CHECK (status IN ('queued','running','done','failed')),
  total_count   INTEGER NOT NULL,
  done_count    INTEGER NOT NULL DEFAULT 0,
  fail_count    INTEGER NOT NULL DEFAULT 0,
  callback_secret_hash TEXT,            -- HMAC of (job_id + shared_secret) — verifies the parser-box callback
  created_at    INTEGER NOT NULL,
  updated_at    INTEGER NOT NULL,
  FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_import_job_user ON import_job(user_id, created_at);

-- ---------- link_job ----------
-- One per URL. envelope_json is the full result from the parser box (see
-- backend/ingest/link-parser/src/schema.js:toEnvelope). We store the entire
-- envelope so the Android review screen can render confidence, warnings,
-- signals_used, etc., without reshaping.
CREATE TABLE IF NOT EXISTS link_job (
  id            TEXT PRIMARY KEY,
  job_id        TEXT NOT NULL,
  user_id       TEXT NOT NULL,
  url           TEXT NOT NULL,
  seq           INTEGER NOT NULL,           -- 0..9 — preserves submission order for the review screen
  status        TEXT NOT NULL DEFAULT 'queued' CHECK (status IN ('queued','running','done','failed','rejected','submitted')),
  envelope_json TEXT,                       -- parser result (success or error)
  submission_id TEXT,                       -- recipe_submission.id once the user confirms + submits
  created_at    INTEGER NOT NULL,
  updated_at    INTEGER NOT NULL,
  FOREIGN KEY (job_id)        REFERENCES import_job(id)        ON DELETE CASCADE,
  FOREIGN KEY (user_id)       REFERENCES user(id)              ON DELETE CASCADE,
  FOREIGN KEY (submission_id) REFERENCES recipe_submission(id) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_link_job_job  ON link_job(job_id, seq);
CREATE INDEX IF NOT EXISTS idx_link_job_user ON link_job(user_id, created_at);
