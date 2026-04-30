-- Flip is_alcoholic=1 on cocktail rows whose ingredients contain a known spirit.
-- Run once: wrangler d1 execute pantrie-db-staging --file=fix-cocktails-alcoholic.sql --remote
--
-- Approach: subquery against recipe_ingredient since SQLite LIKE doesn't support
-- alternation. EXISTS with multiple OR'd LIKE clauses keyed on canonical_name
-- (case-folded by canonicalize() at insert time, so simple lowercase patterns work).
UPDATE recipe
SET is_alcoholic = 1
WHERE content_type = 'cocktail'
  AND COALESCE(is_alcoholic, 0) = 0
  AND EXISTS (
    SELECT 1 FROM recipe_ingredient ri
    WHERE ri.recipe_id = recipe.id
      AND (
        LOWER(ri.canonical_name) LIKE '%vodka%' OR
        LOWER(ri.canonical_name) LIKE '%gin%' OR
        LOWER(ri.canonical_name) LIKE '%rum%' OR
        LOWER(ri.canonical_name) LIKE '%whisk%' OR
        LOWER(ri.canonical_name) LIKE '%tequila%' OR
        LOWER(ri.canonical_name) LIKE '%mezcal%' OR
        LOWER(ri.canonical_name) LIKE '%bourbon%' OR
        LOWER(ri.canonical_name) LIKE '%scotch%' OR
        LOWER(ri.canonical_name) LIKE '%cognac%' OR
        LOWER(ri.canonical_name) LIKE '%brandy%' OR
        LOWER(ri.canonical_name) LIKE '%vermouth%' OR
        LOWER(ri.canonical_name) LIKE '%campari%' OR
        LOWER(ri.canonical_name) LIKE '%aperol%' OR
        LOWER(ri.canonical_name) LIKE '%amaro%' OR
        LOWER(ri.name) LIKE '%vodka%' OR
        LOWER(ri.name) LIKE '%gin%' OR
        LOWER(ri.name) LIKE '%rum%' OR
        LOWER(ri.name) LIKE '%whisk%' OR
        LOWER(ri.name) LIKE '%tequila%' OR
        LOWER(ri.name) LIKE '%mezcal%' OR
        LOWER(ri.name) LIKE '%bourbon%' OR
        LOWER(ri.name) LIKE '%scotch%' OR
        LOWER(ri.name) LIKE '%cognac%' OR
        LOWER(ri.name) LIKE '%brandy%' OR
        LOWER(ri.name) LIKE '%vermouth%' OR
        LOWER(ri.name) LIKE '%campari%' OR
        LOWER(ri.name) LIKE '%aperol%' OR
        LOWER(ri.name) LIKE '%amaro%'
      )
      -- Negative filters: avoid false-positives like "ginger", "ginger ale"
      AND LOWER(COALESCE(ri.canonical_name, ri.name)) NOT LIKE 'ginger%'
      AND LOWER(COALESCE(ri.canonical_name, ri.name)) NOT LIKE '%non-alcoholic%'
      AND LOWER(COALESCE(ri.canonical_name, ri.name)) NOT LIKE '%alcohol-free%'
  );
