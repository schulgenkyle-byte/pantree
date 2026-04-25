-- =====================================================================
-- Fix the 150 cocktails currently mislabeled is_alcoholic = 0
--
-- Root cause: cocktails imported with no is_alcoholic flag set; default 0
--             leaks them into the mocktails bucket and breaks Play Store
--             alcohol-content reporting + kid-safe filtering.
--
-- Strategy: any recipe whose content_type is 'cocktail' AND whose
--           ingredients_json contains a known spirit keyword should be
--           flagged is_alcoholic = 1. Mocktails (content_type='mocktail')
--           are not touched.
--
-- Run via:
--   wrangler d1 execute pantrie-db-staging --remote --file=fix-mislabeled-cocktails.sql
--
-- Or, to dry-run first (returns the rows that would be updated):
--   wrangler d1 execute pantrie-db-staging --remote --command="
--     SELECT id, title, ingredients_json
--       FROM recipe
--      WHERE content_type = 'cocktail' AND COALESCE(is_alcoholic, 0) = 0
--        AND (
--          LOWER(ingredients_json) LIKE '%vodka%' OR
--          LOWER(ingredients_json) LIKE '%gin%' OR
--          LOWER(ingredients_json) LIKE '%rum%' OR
--          LOWER(ingredients_json) LIKE '%whisk%' OR
--          LOWER(ingredients_json) LIKE '%tequila%' OR
--          LOWER(ingredients_json) LIKE '%mezcal%' OR
--          LOWER(ingredients_json) LIKE '%bourbon%' OR
--          LOWER(ingredients_json) LIKE '%scotch%' OR
--          LOWER(ingredients_json) LIKE '%brandy%' OR
--          LOWER(ingredients_json) LIKE '%cognac%' OR
--          LOWER(ingredients_json) LIKE '%vermouth%' OR
--          LOWER(ingredients_json) LIKE '%amaro%' OR
--          LOWER(ingredients_json) LIKE '%campari%' OR
--          LOWER(ingredients_json) LIKE '%aperol%' OR
--          LOWER(ingredients_json) LIKE '%absinthe%' OR
--          LOWER(ingredients_json) LIKE '%curacao%' OR
--          LOWER(ingredients_json) LIKE '%triple sec%' OR
--          LOWER(ingredients_json) LIKE '%liqueur%' OR
--          LOWER(ingredients_json) LIKE '%champagne%' OR
--          LOWER(ingredients_json) LIKE '%prosecco%' OR
--          LOWER(ingredients_json) LIKE '%sake%' OR
--          LOWER(ingredients_json) LIKE '%sherry%' OR
--          LOWER(ingredients_json) LIKE '%port%'
--        )
--      LIMIT 200;
--   "
-- =====================================================================

UPDATE recipe
   SET is_alcoholic = 1
 WHERE content_type = 'cocktail'
   AND COALESCE(is_alcoholic, 0) = 0
   AND (
     LOWER(ingredients_json) LIKE '%vodka%' OR
     LOWER(ingredients_json) LIKE '%gin%' OR
     LOWER(ingredients_json) LIKE '%rum%' OR
     LOWER(ingredients_json) LIKE '%whisk%' OR
     LOWER(ingredients_json) LIKE '%tequila%' OR
     LOWER(ingredients_json) LIKE '%mezcal%' OR
     LOWER(ingredients_json) LIKE '%bourbon%' OR
     LOWER(ingredients_json) LIKE '%scotch%' OR
     LOWER(ingredients_json) LIKE '%brandy%' OR
     LOWER(ingredients_json) LIKE '%cognac%' OR
     LOWER(ingredients_json) LIKE '%vermouth%' OR
     LOWER(ingredients_json) LIKE '%amaro%' OR
     LOWER(ingredients_json) LIKE '%campari%' OR
     LOWER(ingredients_json) LIKE '%aperol%' OR
     LOWER(ingredients_json) LIKE '%absinthe%' OR
     LOWER(ingredients_json) LIKE '%curacao%' OR
     LOWER(ingredients_json) LIKE '%triple sec%' OR
     LOWER(ingredients_json) LIKE '%liqueur%' OR
     LOWER(ingredients_json) LIKE '%champagne%' OR
     LOWER(ingredients_json) LIKE '%prosecco%' OR
     LOWER(ingredients_json) LIKE '%sake%' OR
     LOWER(ingredients_json) LIKE '%sherry%' OR
     LOWER(ingredients_json) LIKE '%port%'
   );

-- Verify after running:
--   wrangler d1 execute pantrie-db-staging --remote --command="
--     SELECT content_type, COALESCE(is_alcoholic,0) AS alcoholic, COUNT(*) AS n
--       FROM recipe GROUP BY content_type, alcoholic ORDER BY 1, 2;
--   "
-- Expected after: cocktail/0 should be near zero (any remaining are likely
-- true mocktails that were just mistagged content_type='cocktail').
