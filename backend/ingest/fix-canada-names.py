"""Strip leading metric prefixes like '0 ml onion powder' → 'onion powder'
from Canada Food Guide ingredient names already in D1."""
import json, io, subprocess, re, sys, os

# Pull the affected rows via wrangler JSON
os.chdir("C:/Users/12566/Downloads/pantrie-build (1)/pantrie-build/backend")
out = subprocess.check_output([
    "npx", "wrangler", "d1", "execute", "pantrie-db-staging",
    "--remote", "--json",
    "--command",
    "SELECT recipe_id, seq, name FROM recipe_ingredient WHERE recipe_id LIKE 'canada-%'"
], shell=True, text=True, encoding="utf-8")

# Output is wrangler JSON envelope: [{"results":[...]}]
data = json.loads(out)
rows = data[0]["results"] if isinstance(data, list) else data.get("results", [])
print(f"loaded {len(rows)} canada ingredient rows")

PREFIX = re.compile(
    r"^\s*\d+(?:\.\d+)?\s*(?:ml|mL|g|kg|l|L|oz|lb|lbs|cup|cups|tsp|tbsp|pinch|dash)\s+",
    re.IGNORECASE,
)

updates = []
for r in rows:
    name = str(r.get("name") or "")
    new = PREFIX.sub("", name).strip()
    # repeat — some lines have two prefixes like "280 ml 2 tsp garlic"
    for _ in range(3):
        stripped = PREFIX.sub("", new).strip()
        if stripped == new: break
        new = stripped
    if new and new != name:
        rid = r["recipe_id"].replace("'", "''")
        nm = new.replace("'", "''")
        updates.append(f"UPDATE recipe_ingredient SET name='{nm}' WHERE recipe_id='{rid}' AND seq={r['seq']};")

sql_path = "ingest/canada-name-fix.sql"
with io.open(sql_path, "w", encoding="utf-8") as f:
    f.write("\n".join(updates))
print(f"wrote {len(updates)} UPDATEs to {sql_path}")
