// Pantrie backend — Cloudflare Worker.
// Routing, config validation, per-route rate limiting, centralized error handling.

import { handleVision, handleReceipt, scanStatus } from './vision.js';
import { handleAuth, requireAuth } from './auth.js';
import { handleUsers } from './users.js';
import { handleRecipes } from './recipes.js';
import { handlePlans } from './plans.js';
import { handleMealPrep } from './mealprep.js';
import { handleShopping } from './shopping.js';
import { handleReviews } from './reviews.js';
import { handleFollows } from './follows.js';
import { handleBilling } from './billing.js';
import { handlePantry } from './pantry.js';
import { handleBarcode } from './barcode.js';
import { handleWaste } from './waste.js';
import { handleNutrition } from './nutrition.js';
import { handleSubstitutions } from './substitutions.js';
import { handleCoreIngredients } from './core-ingredients.js';
import { handleBeta, handleAdmin } from './beta.js';
import { handleSignups } from './signups.js';
import { handlePreferences } from './preferences.js';
import { handleSubmissions, handleSubmissionsAdmin } from './submissions.js';
import { handleImport } from './import.js';
import { handleLibrary } from './library.js';
import { handleGames, GameRoom } from './games.js';
import { json, cors, err, configProblems } from './util.js';
import { enforce, clientIp } from './ratelimit.js';

// Re-export the Durable Object class so wrangler's bindings can find it.
export { GameRoom };

export default {
  async fetch(request, env, ctx) {
    if (request.method === 'OPTIONS') return cors(request, env);

    // Reject critical misconfiguration in prod.
    const envName = (env.ENVIRONMENT || 'prod').toLowerCase();
    const problems = configProblems(env);
    const fatalProblems = problems.filter(p =>
      p.includes('JWT_SECRET') ||
      p.includes('GOOGLE_CLIENT_ID') ||
      p.includes('DB binding') ||
      p.includes('DEV_TOKEN_KEY set in prod')
    );
    if (envName === 'prod' && fatalProblems.length) {
      console.error('config fatal', fatalProblems.join('; '));
      return err(503, 'server misconfigured');
    }

    const url = new URL(request.url);
    const path = url.pathname;

    try {
      // Public, always on
      if (path === '/health' && request.method === 'GET') {
        return json({ ok: true, env: envName, warnings: envName === 'prod' ? undefined : problems }, 200, request, env);
      }

      // Beta signup (public, no auth — landing-page form posts here).
      // Two route aliases: /beta/signup (canonical, used by curl + admin tooling)
      // and /signup (the public-facing path the landing form actually posts to).
      // Keeping both because the landing HTML hardcodes /signup; renaming requires
      // a Pages redeploy whereas adding the alias is one Worker deploy.
      if ((path === '/beta/signup' || path === '/signup') && request.method === 'POST') return handleSignups.create(request, env);
      if ((path === '/signup/count' || path === '/signups/count') && request.method === 'GET') return handleSignups.count(request, env);
      if (path === '/admin/signups' && request.method === 'GET') return handleSignups.list(request, env);
      if (path === '/admin/signups/mark-added' && request.method === 'POST') return handleSignups.markAdded(request, env);

      // Admin (key-gated) — no user auth required, just ADMIN_KEY
      if (path === '/admin/dashboard' && request.method === 'GET') return handleAdmin.dashboard(request, env);
      if (path === '/admin/stats'     && request.method === 'GET') return handleAdmin.stats(request, env);
      if (path === '/admin/sample-recipes' && request.method === 'GET') return handleAdmin.sampleRecipes(request, env);
      if (path === '/admin/photoless-recipes' && request.method === 'GET') return handleAdmin.photolessRecipes(request, env);
      if (path === '/admin/patch-recipe-photo' && request.method === 'POST') return handleAdmin.patchRecipePhoto(request, env);
      if (path === '/admin/fix-content-types' && request.method === 'POST') return handleAdmin.fixContentTypes(request, env);
      if (path === '/admin/purge-broken-recipes' && request.method === 'POST') return handleAdmin.purgeBrokenRecipes(request, env);
      const adminFbMatch = path.match(/^\/admin\/feedback\/([\w-]+)$/);
      if (adminFbMatch && request.method === 'PATCH') return handleAdmin.feedbackUpdate(request, adminFbMatch[1], env);
      if (path === '/admin/submissions' && request.method === 'GET') return handleSubmissionsAdmin.list(request, env);
      const adminSubApprove = path.match(/^\/admin\/submissions\/([\w-]+)\/approve$/);
      if (adminSubApprove && request.method === 'POST') return handleSubmissionsAdmin.approve(request, adminSubApprove[1], env);
      const adminSubReject = path.match(/^\/admin\/submissions\/([\w-]+)\/reject$/);
      if (adminSubReject && request.method === 'POST') return handleSubmissionsAdmin.reject(request, adminSubReject[1], env);
      if (path === '/auth/nonce' && request.method === 'POST') return handleAuth.nonce(request, env);
      if (path === '/auth/google-exchange' && request.method === 'POST') return handleAuth.googleExchange(request, env);
      if (path === '/auth/refresh' && request.method === 'POST') return handleAuth.refresh(request, env);
      if (path === '/auth/dev-token' && request.method === 'POST') return handleAuth.devToken(request, env);
      if (path === '/billing/rtdn' && request.method === 'POST') return handleBilling.rtdn(request, env);

      // Parser-box callback — HMAC-authed, no user session required.
      if (path === '/api/import/callback' && request.method === 'POST') return handleImport.callback(request, env);

      // Mystery Nights — multiplayer game WebSocket upgrade. Public on the
      // /games/<code>/ws path because the code is the credential (you can't
      // join a game without it). The host_create endpoint downstream is
      // gated by requireAuth.
      const gameWsMatch = path.match(/^\/games\/([A-Z2-9]{4})\/ws$/);
      if (gameWsMatch && request.method === 'GET') {
        return handleGames.wsUpgrade(request, env, gameWsMatch[1]);
      }

      // Public Library book by id — shareable URL, no auth.
      // .html → server-rendered HTML for browsers + crawlers. Plain id → JSON for clients.
      const publicBookHtmlMatch = path.match(/^\/b\/([\w-]+)\.html$/);
      if (publicBookHtmlMatch && request.method === 'GET') return handleLibrary.getPublicBookHtml(publicBookHtmlMatch[1], env, request);
      const publicBookMatch = path.match(/^\/b\/([\w-]+)$/);
      if (publicBookMatch && request.method === 'GET') {
        const accept = request.headers.get('accept') || '';
        if (accept.includes('text/html')) return handleLibrary.getPublicBookHtml(publicBookMatch[1], env, request);
        return handleLibrary.getPublicBook(publicBookMatch[1], env, request);
      }

      // Public, rate-limited
      if (path === '/recipes/seed' && request.method === 'POST') return handleRecipes.seed(request, env);
      if (path === '/admin/recanonicalize' && request.method === 'POST') return handleRecipes.recanonicalize(request, env);

      // Authed routes
      const auth = await requireAuth(request, env);
      if (auth.error) return err(401, auth.error);
      const userId = auth.userId;
      const authPayload = auth.payload;

      // Also apply a baseline per-user envelope limit so no one route bypass lets you flood.
      const envelope = await enforce(env, 'read', userId);
      if (envelope) return envelope;

      if (path === '/scan' && request.method === 'POST') return handleVision(request, env, userId);
      if (path === '/scan/receipt' && request.method === 'POST') return handleReceipt(request, env, userId);
      if (path === '/scan/status' && request.method === 'GET') return scanStatus(userId, env, request);

      if (path === '/barcode/lookup' && request.method === 'POST') return handleBarcode.lookup(request, userId, env);

      if (path === '/pantry' && request.method === 'GET') return handlePantry.list(userId, env, request);
      if (path === '/me/core-ingredients' && request.method === 'GET') return handleCoreIngredients.get(userId, env, request);
      if (path === '/pantry' && request.method === 'POST') return handlePantry.add(request, userId, env);
      if (path === '/pantry/bulk' && request.method === 'POST') return handlePantry.addBulk(request, userId, env);
      const pantryMatch = path.match(/^\/pantry\/([\w-]+)$/);
      if (pantryMatch && request.method === 'PATCH') return handlePantry.update(pantryMatch[1], request, userId, env);
      if (pantryMatch && request.method === 'DELETE') return handlePantry.delete(pantryMatch[1], userId, env, request);

      if (path === '/waste/log' && request.method === 'POST') return handleWaste.log(request, userId, env);
      if (path === '/waste/summary' && request.method === 'GET') return handleWaste.summary(request, userId, env);
      if (path === '/waste/history' && request.method === 'GET') return handleWaste.history(request, userId, env);

      const nutritionMatch = path.match(/^\/recipes\/([\w-]+)\/nutrition$/);
      if (nutritionMatch && request.method === 'GET') return handleNutrition.get(nutritionMatch[1], userId, env, request);

      // Match anything that isn't a path separator. Old `[\w\-.]+` regex
      // 404'd on every multi-word ingredient ("black pepper" → URL-encoded
      // "/substitutions/black%20pepper" — the % wasn't in the char class
      // and the regex bailed out). decodeURIComponent runs in the handler.
      const subMatch = path.match(/^\/substitutions\/([^/]+)$/);
      if (subMatch && request.method === 'GET') return handleSubstitutions.get(decodeURIComponent(subMatch[1]), userId, env, request);

      if (path === '/me' && request.method === 'GET') return handleUsers.me(userId, env, request);
      if (path === '/me/home' && request.method === 'GET') return handleUsers.home(userId, env, request);
      if (path === '/me' && request.method === 'PATCH') return handleUsers.update(request, userId, env);
      if (path === '/me' && request.method === 'DELETE') return handleUsers.delete(request, userId, env, authPayload);
      if (path === '/me/export' && request.method === 'POST') return handleUsers.export(userId, env, request);
      if (path === '/auth/reauth' && request.method === 'POST') return handleAuth.reauth(request, env, userId);
      if (path === '/auth/logout' && request.method === 'POST') return handleAuth.logout(request, userId, env, authPayload);
      if (path === '/auth/logout-all' && request.method === 'POST') return handleAuth.logoutAll(request, userId, env, authPayload);

      if (path === '/recipes/deck' && request.method === 'GET') return handleRecipes.deck(userId, env, request);
      if (path === '/recipes/search' && request.method === 'GET') return handleRecipes.search(request, userId, env);
      if (path === '/me/saved' && request.method === 'GET') return handleRecipes.saved(userId, env, request);
      if (path === '/interactions/reshop' && request.method === 'POST') return handleRecipes.reshop(request, userId, env);
      const recipeMatch = path.match(/^\/recipes\/([\w-]+)$/);
      if (recipeMatch && request.method === 'GET') return handleRecipes.get(recipeMatch[1], userId, env, request);

      if (path === '/interactions' && request.method === 'POST') return handleRecipes.interact(request, userId, env);
      if (path === '/interactions/undo' && request.method === 'POST') return handleRecipes.undoCook(request, userId, env);

      // ---------- Library: three-level (Library → Books → Chapters → Recipes) ----------
      if (path === '/api/library' && request.method === 'GET') return handleLibrary.get(userId, env, request);
      if (path === '/api/library/books' && request.method === 'POST') return handleLibrary.createBook(request, userId, env);
      const libBookExportMatch = path.match(/^\/api\/library\/books\/([\w-]+)\/export$/);
      if (libBookExportMatch && request.method === 'GET') return handleLibrary.exportBook(libBookExportMatch[1], userId, env, request);
      const libBookForkMatch = path.match(/^\/api\/library\/books\/([\w-]+)\/fork$/);
      if (libBookForkMatch && request.method === 'POST') return handleLibrary.forkBook(libBookForkMatch[1], userId, env, request);
      const libChapterRecipeMatch = path.match(/^\/api\/library\/books\/([\w-]+)\/chapters\/([\w-]+)\/recipes\/([\w-]+)$/);
      if (libChapterRecipeMatch && request.method === 'DELETE') return handleLibrary.removeRecipe(libChapterRecipeMatch[1], libChapterRecipeMatch[2], libChapterRecipeMatch[3], userId, env, request);
      const libChapterRecipesMatch = path.match(/^\/api\/library\/books\/([\w-]+)\/chapters\/([\w-]+)\/recipes$/);
      if (libChapterRecipesMatch && request.method === 'POST') return handleLibrary.addRecipe(libChapterRecipesMatch[1], libChapterRecipesMatch[2], request, userId, env);
      const libChapterMatch = path.match(/^\/api\/library\/books\/([\w-]+)\/chapters\/([\w-]+)$/);
      if (libChapterMatch && request.method === 'PUT')    return handleLibrary.updateChapter(libChapterMatch[1], libChapterMatch[2], request, userId, env);
      if (libChapterMatch && request.method === 'DELETE') return handleLibrary.deleteChapter(libChapterMatch[1], libChapterMatch[2], userId, env, request);
      const libChaptersMatch = path.match(/^\/api\/library\/books\/([\w-]+)\/chapters$/);
      if (libChaptersMatch && request.method === 'POST')  return handleLibrary.createChapter(libChaptersMatch[1], request, userId, env);
      const libBookMatch = path.match(/^\/api\/library\/books\/([\w-]+)$/);
      if (libBookMatch && request.method === 'GET')       return handleLibrary.getBook(libBookMatch[1], userId, env, request);
      if (libBookMatch && request.method === 'PUT')       return handleLibrary.updateBook(libBookMatch[1], request, userId, env);
      if (libBookMatch && request.method === 'DELETE')    return handleLibrary.deleteBook(libBookMatch[1], userId, env, request);

      if (path === '/plans' && request.method === 'GET') return handlePlans.list(userId, env, request);
      if (path === '/plans' && request.method === 'POST') return handlePlans.create(request, userId, env);
      if (path === '/plans/propose' && request.method === 'GET') return handlePlans.propose(userId, env, request);
      if (path === '/plans/alternatives' && request.method === 'POST') return handlePlans.alternatives(request, userId, env);
      if (path === '/plans/meal-prep' && request.method === 'POST') return handleMealPrep.propose(request, userId, env);
      const planMatch = path.match(/^\/plans\/([\w-]+)$/);
      if (planMatch && request.method === 'DELETE') return handlePlans.delete(planMatch[1], userId, env, request);

      if (path === '/shopping' && request.method === 'GET') return handleShopping.list(userId, env, request);
      if (path === '/shopping/smart' && request.method === 'GET') return handleShopping.smart(userId, env, request);
      if (path === '/shopping' && request.method === 'POST') return handleShopping.add(request, userId, env);
      if (path === '/shopping' && request.method === 'DELETE') return handleShopping.clear(userId, env, request);
      if (path === '/shopping/vendor-handoff' && request.method === 'POST') return handleShopping.vendorHandoff(request, userId, env);
      const shopMatch = path.match(/^\/shopping\/([\w-]+)$/);
      if (shopMatch && request.method === 'PATCH') return handleShopping.update(shopMatch[1], request, userId, env);
      if (shopMatch && request.method === 'DELETE') return handleShopping.delete(shopMatch[1], userId, env, request);

      if (path === '/reviews' && request.method === 'POST') return handleReviews.create(request, userId, env);
      if (path === '/reviews' && request.method === 'GET') return handleReviews.feed(userId, env, request);
      const reviewMatch = path.match(/^\/reviews\/([\w-]+)$/);
      if (reviewMatch && request.method === 'DELETE') return handleReviews.delete(reviewMatch[1], userId, env, request);
      const reportMatch = path.match(/^\/reviews\/([\w-]+)\/report$/);
      if (reportMatch && request.method === 'POST') return handleReviews.report(reportMatch[1], request, userId, env);

      const followMatch = path.match(/^\/follows\/([\w-]+)$/);
      if (followMatch && request.method === 'POST') return handleFollows.follow(followMatch[1], userId, env, request);
      if (followMatch && request.method === 'DELETE') return handleFollows.unfollow(followMatch[1], userId, env, request);

      const blockMatch = path.match(/^\/blocks\/([\w-]+)$/);
      if (blockMatch && request.method === 'POST') return handleFollows.block(blockMatch[1], userId, env, request);
      if (blockMatch && request.method === 'DELETE') return handleFollows.unblock(blockMatch[1], userId, env, request);

      // Beta
      if (path === '/beta/feedback' && request.method === 'POST') return handleBeta.feedback(request, userId, env);
      if (path === '/beta/activity' && request.method === 'GET')  return handleBeta.activity(request, userId, env);
      if (path === '/community/reviews' && request.method === 'GET') return handleBeta.reviewFeed(request, userId, env);
      if (path === '/events'        && request.method === 'POST') return handleBeta.events(request, userId, env);

      // Submissions (user-generated recipes)
      if (path === '/submissions/photo'  && request.method === 'POST') return handleSubmissions.uploadPhoto(request, userId, env);
      if (path === '/submissions/recipe' && request.method === 'POST') return handleSubmissions.submitRecipe(request, userId, env);
      if (path === '/submissions/mine'   && request.method === 'GET')  return handleSubmissions.mine(userId, env, request);

      // Recipe photo contributions — user submits a photo for an EXISTING recipe.
      // If approved, becomes the recipe's canonical image with photo credit.
      const contribPhotoMatch = path.match(/^\/recipes\/([\w-]+)\/contribute-photo$/);
      if (contribPhotoMatch && request.method === 'POST') {
        return handleSubmissions.contributePhoto(request, userId, env, contribPhotoMatch[1]);
      }

      // Pro photo-to-recipe (Speakeater Pro): Vision extracts a draft + structured submit.
      // Both Pro-gated server-side; client should also gate via EntitlementRepository.
      if (path === '/me/extract-recipe-from-photo' && request.method === 'POST') return handleSubmissions.extractRecipeFromPhoto(request, userId, env);
      if (path === '/me/submit-recipe' && request.method === 'POST') return handleSubmissions.submitStructuredRecipe(request, userId, env);

      // Link-import (TikTok/YouTube → recipe_submission). Pro-gated server-side.
      if (path === '/api/import/links' && request.method === 'POST') return handleImport.create(request, userId, env, ctx);
      const importJobMatch = path.match(/^\/api\/import\/jobs\/([\w-]+)$/);
      if (importJobMatch && request.method === 'GET') return handleImport.get(importJobMatch[1], request, userId, env);
      const linkSubmitMatch = path.match(/^\/api\/import\/links\/([\w-]+)\/submit$/);
      if (linkSubmitMatch && request.method === 'POST') return handleImport.submit(linkSubmitMatch[1], request, userId, env);
      const linkRejectMatch = path.match(/^\/api\/import\/links\/([\w-]+)\/reject$/);
      if (linkRejectMatch && request.method === 'POST') return handleImport.reject(linkRejectMatch[1], request, userId, env);

      if (path === '/billing/verify' && request.method === 'POST') return handleBilling.verify(request, userId, env);
      if (path === '/me/entitlement' && request.method === 'GET') return handleBilling.entitlement(userId, env, request);
      // Dev-only — gated server-side on env.ENVIRONMENT === 'dev'.
      if (path === '/me/debug/grant-pro' && request.method === 'POST') return handleBilling.debugGrantPro(userId, env, request);
      if (path === '/me/debug/revoke-pro' && request.method === 'POST') return handleBilling.debugRevokePro(userId, env, request);

      if (path === '/me/preferences' && request.method === 'GET')  return handlePreferences.get(userId, env, request);
      if (path === '/me/preferences' && request.method === 'PUT')  return handlePreferences.put(request, userId, env);
      if (path === '/me/taste'       && request.method === 'GET')  return handlePreferences.taste(userId, env, request);

      // Mystery Nights — host endpoint (requires logged-in user). Players
      // join via the public /games/<code>/ws WebSocket above using the code
      // as their credential.
      if (path === '/games/create' && request.method === 'POST') return handleGames.create(request, userId, env);

      return err(404, 'not found');
    } catch (e) {
      // Correlation ID — log internally, surface only the ID to the client.
      const correlationId = crypto.randomUUID();
      console.error('fatal', correlationId, e?.message);
      return err(500, 'internal error', { correlationId });
    }
  },
};
