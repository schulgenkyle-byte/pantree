#!/usr/bin/env bash
# Loops through all historic cocktails needing modernization and updates them
# in batches of 200.
set -e
cd "$(dirname "$0")"

BATCH=0
TOTAL_MOD=0
TOTAL_SKIP=0
while : ; do
  BATCH=$((BATCH+1))
  echo "=== Batch ${BATCH} ==="
  npx wrangler d1 execute pantrie-db-staging --remote \
    --command "SELECT id, title, original_text, source_year, source_book, glass_type FROM recipe WHERE is_historic=1 AND (modernized_text IS NULL OR modernized_text = '') LIMIT 200" \
    --json > /tmp/batch_${BATCH}.json

  COUNT=$(node -e "const d=require('/tmp/batch_${BATCH}.json'); const r=Array.isArray(d)?d[0].results:d.results; console.log(r.length);")
  echo "Rows in batch: ${COUNT}"
  if [ "$COUNT" = "0" ]; then
    echo "Done!"
    break
  fi

  node modernize_cocktails.js /tmp/batch_${BATCH}.json /tmp/batch_${BATCH}.sql > /tmp/batch_${BATCH}.stats.json
  cat /tmp/batch_${BATCH}.stats.json

  MOD=$(node -e "console.log(require('/tmp/batch_${BATCH}.stats.json').modernized)")
  SKIP=$(node -e "console.log(require('/tmp/batch_${BATCH}.stats.json').skipped)")
  TOTAL_MOD=$((TOTAL_MOD+MOD))
  TOTAL_SKIP=$((TOTAL_SKIP+SKIP))

  # Apply the UPDATE file against D1.
  npx wrangler d1 execute pantrie-db-staging --remote --file /tmp/batch_${BATCH}.sql

  if [ "$BATCH" -ge 10 ]; then
    echo "Safety cap: stopping after 10 batches (you can re-run to continue)."
    break
  fi
done

echo "=== SUMMARY ==="
echo "Total modernized: ${TOTAL_MOD}"
echo "Total skipped: ${TOTAL_SKIP}"
