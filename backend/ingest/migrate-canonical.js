// One-shot migration: compute canonical_name for every existing recipe_ingredient + pantry_item.
// Writes a batch of UPDATE statements to stdout (pipe into wrangler).
//
// Usage:
//   node ingest/migrate-canonical.js > migrate.sql
//   npx wrangler d1 execute pantrie-db-staging --file=migrate.sql --remote
//
// Or, streaming via a Worker migration endpoint (simpler):
//   MIGRATE_KEY=<seed-key> BASE=<worker-url> node ingest/migrate-canonical.js

// We rely on the backend endpoint instead — simpler, no SQL file juggling.
// This is here as documentation; actual migration fires via the /admin/recanonicalize endpoint.

console.log('Use POST /admin/recanonicalize with x-seed-key header to run migration server-side.');
