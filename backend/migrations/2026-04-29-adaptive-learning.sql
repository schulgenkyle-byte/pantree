-- Adaptive learning + vendor handoff schema. Idempotent.

CREATE TABLE IF NOT EXISTS user_taste (
  user_id TEXT PRIMARY KEY,
  cuisine_weights_json TEXT,
  ingredient_family_weights_json TEXT,
  complexity_tolerance REAL,
  time_tolerance_min REAL,
  spicy_tolerance REAL,
  total_signals INTEGER DEFAULT 0,
  last_computed_at INTEGER,
  FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_taste_signal (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id TEXT NOT NULL,
  recipe_id TEXT NOT NULL,
  signal_kind TEXT NOT NULL,
  weight REAL NOT NULL DEFAULT 1.0,
  created_at INTEGER NOT NULL,
  consumed_at INTEGER,
  FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_taste_signal_user ON user_taste_signal(user_id, created_at);

CREATE TABLE IF NOT EXISTS vendor_handoff (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id TEXT NOT NULL,
  vendor_id TEXT NOT NULL,
  item_count INTEGER NOT NULL,
  category_count INTEGER,
  estimated_total_cents INTEGER,
  created_at INTEGER NOT NULL,
  FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_vendor_handoff_user ON vendor_handoff(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_vendor_handoff_vendor ON vendor_handoff(vendor_id, created_at);
