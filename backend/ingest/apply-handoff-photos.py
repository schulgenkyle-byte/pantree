"""Apply photo updates from the pantree-drinks-handoff folder to D1.
Joins by titleMatched (case-insensitive), since handoff used a different recipeId scheme.
"""
import json, io, os, sys

HANDOFF = "C:/Users/12566/pantree-drinks-handoff"
OUT = "C:/Users/12566/Downloads/pantrie-build (1)/pantrie-build/backend/ingest/handoff-photos-apply.sql"

# Trusted files only (skip agent4 garbage, agent6 license-questionable)
SOURCES = [
    "agent1-photo-fills.json",
    "agent3-photos-strict.json",
    "agent7-photos.json",
]

def sqlesc(s):
    if s is None: return "NULL"
    return "'" + str(s).replace("'", "''") + "'"

total = 0
seen = set()
with io.open(OUT, "w", encoding="utf-8") as out:
    for src in SOURCES:
        path = os.path.join(HANDOFF, src)
        if not os.path.exists(path):
            print(f"skip missing {src}", file=sys.stderr)
            continue
        with io.open(path, encoding="utf-8") as f:
            photos = json.load(f)
        for p in photos:
            title = p.get("titleMatched") or p.get("title")
            url = p.get("imageUrl") or p.get("image_url")
            if not title or not url:
                continue
            key = title.strip().lower()
            if key in seen: continue
            seen.add(key)
            cred = p.get("author") or p.get("photo_credit")
            lic = p.get("licenseShort") or p.get("license") or p.get("photo_license")
            srcurl = p.get("sourceUrl") or p.get("source_url") or p.get("photo_source_url")
            # Update first cocktail recipe matching the title that has no photo yet.
            out.write(
                f"UPDATE recipe SET image_url={sqlesc(url)}, photo_credit={sqlesc(cred)}, "
                f"photo_license={sqlesc(lic)}, photo_source_url={sqlesc(srcurl)} "
                f"WHERE id IN (SELECT id FROM recipe WHERE LOWER(title)={sqlesc(key)} "
                f"AND content_type IN ('cocktail','mocktail') "
                f"AND (image_url IS NULL OR image_url='') LIMIT 1);\n"
            )
            total += 1
print(f"wrote {total} title-match UPDATEs to {OUT}")
