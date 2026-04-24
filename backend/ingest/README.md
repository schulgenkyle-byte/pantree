# Pantrie recipe ingest pipeline

Free + legal recipe sources for the Pantrie catalog. Run fetchers, normalizers, uploaders.

## Sources

| Source | Recipes | License | Cost | Approx time |
|---|---|---|---|---|
| TheMealDB | ~300 | CC-BY-SA | $0 | 3 min |
| Wikibooks Cookbook | ~2,800 | CC-BY-SA 3.0 | ~$7 Haiku | 45 min |
| HuggingFace RecipeNLG (default 100k subset; full 2.2M available) | 100,000 | MIT | $0 | 2-3 hours |

## Prereqs

From `backend/`:
1. `SEED_KEY` secret must be set on the Worker. If you deleted it, put a new one:
   - `openssl rand -hex 16`
   - `npx wrangler secret put SEED_KEY` → paste
   - `npm run deploy`
2. Anthropic key in your shell for Wikibooks normalization:
   - `export ANTHROPIC_API_KEY=sk-ant-...`
3. Your Worker URL and seed key in your shell for uploads:
   - `export BASE=https://pantrie-backend.<sub>.workers.dev`
   - `export SEED_KEY=<the hex you put above>`

## Run (in order)

### 1. TheMealDB — fast, free, start here
```bash
npm run ingest:themealdb
npm run ingest:upload:themealdb
```
Expected: ~290 recipes upserted.

### 2. Wikibooks Cookbook — $7 Haiku cost
```bash
npm run ingest:wikibooks
npm run ingest:upload:wikibooks
```
Expected: ~2,500 recipes (some pages aren't actual recipes and get filtered).

Check cost at https://console.anthropic.com/settings/usage before kicking off.
If you get worried, set `INGEST_LIMIT=200` env var to test on a subset first.

### 3. Hugging Face RecipeNLG — 100k free (millions available)
```bash
npm run ingest:huggingface
npm run ingest:upload:huggingface
```
Expected: ~95,000-100,000 recipes.

For the full 2.2M corpus, set `HF_LIMIT=0` and upgrade to Workers Paid ($5/mo):
```bash
HF_LIMIT=0 npm run ingest:huggingface
```

### 4. Or everything at once
```bash
npm run ingest:themealdb
ANTHROPIC_API_KEY=sk-ant-... npm run ingest:wikibooks
npm run ingest:huggingface
npm run ingest:upload:all
```

## Troubleshooting

**"403 forbidden"**: `SEED_KEY` mismatch. Re-put the secret and re-deploy.
**"404 not found"**: Worker `ENVIRONMENT=prod` disables seeding. Set `ENVIRONMENT=dev` in wrangler.toml and redeploy.
**"429 rate limited"**: Upload batches lowered automatically. Reduce `CONCURRENCY=1` for slower uploads.
**HF dataset 404**: Some datasets require auth. Pick a different dataset or set `HF_TOKEN=hf_...`.

## D1 quota notes

Free tier: 5 GB storage, 50K row writes/day. Each recipe creates:
- 1 row in `recipe`
- ~10 rows in `recipe_ingredient`
- ~8 rows in `recipe_step`
≈ 19 rows per recipe.

So 100k recipes = ~1.9M row writes. At 50k/day free = 38 days of writes.

Workers Paid ($5/mo) = 50M row writes/month → full 2.2M ingest finishes in a day.

## Resume

Wikibooks and HF normalizers checkpoint every N items to `raw/*-checkpoint.json`. Re-running the same command picks up where it stopped.
