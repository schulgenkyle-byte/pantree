"""Generate SQL UPDATE file for Wikimedia photos harvested."""
import json, io, sys

SRC = "C:/Users/12566/Downloads/pantrie-build (1)/pantrie-build/backend/ingest/normalized/wikimedia-photos.json"
OUT = "C:/Users/12566/Downloads/pantrie-build (1)/pantrie-build/backend/ingest/wiki-photos-apply.sql"

def sqlesc(s):
    if s is None: return "NULL"
    return "'" + str(s).replace("'", "''") + "'"

with io.open(SRC, encoding="utf-8") as f:
    photos = json.load(f)

with io.open(OUT, "w", encoding="utf-8") as out:
    for p in photos:
        rid = sqlesc(p["recipeId"])
        url = sqlesc(p["imageUrl"])
        cred = sqlesc(p.get("author"))
        lic = sqlesc(p.get("licenseShort"))
        src = sqlesc(p.get("sourceUrl"))
        out.write(
            f"UPDATE recipe SET image_url={url}, photo_credit={cred}, "
            f"photo_license={lic}, photo_source_url={src} "
            f"WHERE id={rid} AND (image_url IS NULL OR image_url='');\n"
        )
print(f"wrote {len(photos)} UPDATEs to {OUT}")
