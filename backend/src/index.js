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
import { handlePreferences } from './preferences.js';
import { handleSubmissions, handleSubmissionsAdmin } from './submissions.js';
import { json, cors, err, configProblems } from './util.js';
import { enforce, clientIp } from './ratelimit.js';

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

      // Admin (key-gated) — no user auth required, just ADMIN_KEY
      if (path === '/admin/dashboard' && request.method === 'GET') return handleAdmin.dashboard(request, env);
      if (path === '/admin/stats'     && request.method === 'GET') return handleAdmin.stats(request, env);
      if (path === '/admin/sample-recipes' && request.method === 'GET') return handleAdmin.sampleRecipes(request, env);
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

      const subMatch = path.match(/^\/substitutions\/([\w\-.]+)$/);
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

      if (path === '/billing/verify' && request.method === 'POST') return handleBilling.verify(request, userId, env);
      if (path === '/me/entitlement' && request.method === 'GET') return handleBilling.entitlement(userId, env, request);

      if (path === '/me/preferences' && request.method === 'GET')  return handlePreferences.get(userId, env, request);
      if (path === '/me/preferences' && request.method === 'PUT')  return handlePreferences.put(request, userId, env);
      if (path === '/me/taste'       && request.method === 'GET')  return handlePreferences.taste(userId, env, request);

      return err(404, 'not found');
    } catch (e) {
      // Correlation ID — log internally, surface only the ID to the client.
      const correlationId = crypto.randomUUID();
      console.error('fatal', correlationId, e?.message);
      return err(500, 'internal error', { correlationId });
    }
  },
};
