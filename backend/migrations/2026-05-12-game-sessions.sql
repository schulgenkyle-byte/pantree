-- Mystery Nights — game session record.
--
-- Durable Objects hold the live game state (in-memory + DO storage).
-- After a game completes, the DO can write a summary row to this D1 table
-- for long-term record + the "share your night" recap surface.
--
-- This table is NOT in the hot path — writes happen once per game at reveal,
-- reads happen rarely (recap viewer + analytics).

CREATE TABLE IF NOT EXISTS game_session (
  id                TEXT PRIMARY KEY,             -- UUID
  code              TEXT NOT NULL,                -- 4-letter room code
  menu_id           TEXT NOT NULL,                -- 'murder_algonquin' / etc.
  host_user_id      TEXT,                         -- Speakeater user who hosted
  player_count      INTEGER NOT NULL,
  started_at        INTEGER NOT NULL,
  completed_at      INTEGER,
  duration_minutes  INTEGER,
  ending_id         TEXT,                         -- which reveal ending fired
  beat_history_json TEXT,                         -- compact JSON of beat/action timeline
  shared_at         INTEGER,                      -- when the host shared the recap
  FOREIGN KEY (host_user_id) REFERENCES user(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_game_session_host
  ON game_session(host_user_id, completed_at);

CREATE INDEX IF NOT EXISTS idx_game_session_menu
  ON game_session(menu_id, completed_at);
