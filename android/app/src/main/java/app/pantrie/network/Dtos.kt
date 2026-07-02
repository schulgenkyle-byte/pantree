package app.pantrie.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NonceResponse(val nonce: String, val expiresIn: Int)

@Serializable
data class GoogleExchangeRequest(
  val idToken: String,
  val nonce: String? = null,
  val integrityToken: String? = null,
)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class LogoutRequest(val refreshToken: String)

@Serializable
data class ReauthRequest(val idToken: String, val nonce: String? = null)

@Serializable
data class ReauthResponse(val reauthToken: String, val expiresAt: Long)

@Serializable
data class DeleteAccountRequest(val reauthToken: String)

@Serializable
data class DevTokenRequest(val email: String = "dev@pantrie.test")

@Serializable
data class SessionResponse(
  val userId: String,
  val accessToken: String,
  val refreshToken: String,
  val expiresAt: Long,
)

@Serializable
data class GenericOk(val ok: Boolean = true, val error: String? = null)

@Serializable
data class User(
  val id: String,
  val email: String,
  val displayName: String? = null,
  val bio: String? = null,
  val diet: String? = null,
  val skillLevel: String? = null,
  val allergies: List<String> = emptyList(),
)

@Serializable
data class UpdateMeRequest(
  val displayName: String? = null,
  val bio: String? = null,
  val diet: String? = null,
  val skillLevel: String? = null,
  val allergies: List<String>? = null,
)

// Scan
@Serializable
data class ScanRequest(
  val image: String,                          // data:image/jpeg;base64,...
  val mode: String = "multi",
  @SerialName("existing_items") val existingItems: List<String> = emptyList(),
)

@Serializable
data class DetectedItem(
  val name: String,
  val category: String,
  @SerialName("quantity_seen") val quantitySeen: Int = 1,
  val unit: String? = null,
  val confidence: String? = null,
  @SerialName("matches_existing") val matchesExisting: String? = null,
  // Canonical slug from backend's `canonicalize()` — used by the multi-photo dedup
  // confirmation dialog to detect when the same physical item (e.g., one egg carton)
  // shows up in 2+ photos within a single batch. Falls back to lowercased name if absent.
  @SerialName("canonical_slug") val canonicalSlug: String? = null,
)

@Serializable
data class ScanResponse(val ok: Boolean, val items: List<DetectedItem> = emptyList(), val error: String? = null, val dailyLimit: Int? = null)

@Serializable
data class ScanStatusResponse(val used: Int, val limit: Int, val resetAt: Long)

// Recipes
@Serializable
data class Recipe(
  val id: String,
  val title: String,
  val cuisine: String? = null,
  val description: String? = null,
  val skillLevel: String? = null,
  val prepMinutes: Int? = null,
  val cookMinutes: Int? = null,
  val servings: Int? = null,
  val avgRating: Double? = null,
  val totalRatings: Int? = null,
  val dietaryFlags: List<String> = emptyList(),
  val allergenWarnings: List<String> = emptyList(),
  val ingredients: List<Ingredient> = emptyList(),
  val steps: List<Step> = emptyList(),
  val pantryMatchPercent: Int = 0,
  val usesExpiring: List<String> = emptyList(),
  val cookCount: Int = 0,
  val imageUrl: String? = null,
  val photoCredit: String? = null,
  val photoLicense: String? = null,
  val photoSourceUrl: String? = null,
  val attribution: String? = null,
  val maxServings: Int? = null,
  val costPerServing: Double = 0.0,
  val shortestExpiryDays: Int? = null,
  // Cocktail / historic metadata — populated for Mixology content only.
  @SerialName("content_type") val contentType: String = "food",
  @SerialName("is_historic") val isHistoric: Boolean = false,
  @SerialName("is_alcoholic") val isAlcoholic: Boolean = false,
  @SerialName("source_year") val sourceYear: Int? = null,
  @SerialName("source_book") val sourceBook: String? = null,
  @SerialName("source_region") val sourceRegion: String? = null,
  @SerialName("contributor_name") val contributorName: String? = null,
  @SerialName("contributor_story") val contributorStory: String? = null,
  @SerialName("original_text") val originalText: String? = null,
  @SerialName("modernized_text") val modernizedText: String? = null,
  @SerialName("glass_type") val glassType: String? = null,
  val method: String? = null,
  val garnish: String? = null,
  @SerialName("abv_percent") val abvPercent: Double? = null,
  // Social signals — TikTok-style counts on the card.
  @SerialName("pan_count") val panCount: Int = 0,
  @SerialName("save_count") val saveCount: Int = 0,
  // First-cook claim. If null, the recipe is unclaimed: next cooker gets
  // their name on the card forever, and the photo-less placeholder shows
  // a "Cook this, get your name on the card" CTA.
  @SerialName("first_cooked_by_display_name") val firstCookedBy: String? = null,
  // Server-computed allergen banner driver: "none" | "yellow" | "red".
  //   yellow → allergen present but every matched allergen has known subs (block w/ workaround)
  //   red    → at least one matched allergen has no real sub (skip recipe)
  val allergenStatus: String = "none",
  val allergenLabels: List<String> = emptyList(),
) {
  // Convenience accessors so UI code stays simple
  val prepMin: Int get() = prepMinutes ?: 0
  val cookMin: Int get() = cookMinutes ?: 0
  val totalMin: Int get() = prepMin + cookMin
  val serves: Int get() = servings ?: 2
  val rating: Double get() = avgRating ?: 0.0
  val ratingCount: Int get() = totalRatings ?: 0
}

@Serializable
data class Ingredient(
  val name: String,
  val quantity: Double? = null,
  val unit: String? = null,
  val aisle: String? = null,
  val have: Boolean = false,
  val subs: List<String> = emptyList(),
)

@Serializable
data class Step(val order: Int, val text: String, val timerSeconds: Int? = null)

@Serializable
data class DeckResponse(
  val deck: List<Recipe>,
  val dailyCap: Int = 10,
  val remaining: Int = 10,
  val resetAt: Long = 0,
  val tier: String = "free",
  val message: String? = null,
)

@Serializable
data class HomeStats(
  val pantryCount: Int = 0,
  val expiringSoon: Int = 0,
  val expired: Int = 0,
  val daysSinceScan: Int? = null,
  val savedThisWeekUsd: Double = 0.0,
  val tier: String = "free",
  val scanNudge: NudgeBanner = NudgeBanner(),
  val expiringNudge: NudgeBanner = NudgeBanner(),
)

@Serializable
data class NudgeBanner(val show: Boolean = false, val message: String = "")

@Serializable
data class InteractResponse(
  val ok: Boolean = true,
  val remaining: Int? = null,
  val dailyCap: Int? = null,
  val addedToShopping: Int = 0,
  val cookUndoId: String? = null,
)

@Serializable
data class UndoCookRequest(val cookUndoId: String)

@Serializable
data class InteractRequest(
  val recipeId: String,
  val status: String,
  val dismissReason: String? = null,
  // Optional free-text capturing what the user swapped while cooking. Only set
  // when status='cooked'. Null/empty = no swaps. Cap mirrors backend's 240-char limit.
  val substitutesUsed: String? = null,
)

// Shopping
@Serializable
data class ShoppingItem(
  val id: String,
  val name: String,
  val quantity: Double? = null,
  val unit: String? = null,
  val aisle: String? = null,
  val checked: Boolean = false,
  val source: String = "manual",
)

@Serializable
data class ShoppingResponse(val items: List<ShoppingItem>)

@Serializable
data class ExpiringItem(
  val id: String,
  val name: String,
  val category: String? = null,
  val quantity: Double? = null,
  val unit: String? = null,
  val daysLeft: Int,
)

@Serializable
data class UnlockSuggestion(
  val ingredient: String,
  val count: Int,
  val recipes: List<RecipeMini> = emptyList(),
)

@Serializable
data class RecipeMini(val id: String, val title: String)

@Serializable
data class SmartShoppingResponse(
  val items: List<ShoppingItem> = emptyList(),
  val expiring: List<ExpiringItem> = emptyList(),
  val unlocks: List<UnlockSuggestion> = emptyList(),
)

@Serializable
data class PlanProposal(
  val id: String,
  val title: String,
  val cuisine: String? = null,
  val prepMinutes: Int? = null,
  val cookMinutes: Int? = null,
  val servings: Int? = null,
  val pantryMatchPercent: Int = 0,
  val usesExpiring: List<String> = emptyList(),
  val reason: String = "",
)

@Serializable
data class PlanProposalResponse(val proposals: List<PlanProposal>)

@Serializable
data class PlanAlternativesRequest(
  val recipeId: String,
  val excludeIds: List<String> = emptyList(),
)

@Serializable
data class PlanAlternative(
  val id: String,
  val title: String,
  val cuisine: String? = null,
  val prepMinutes: Int? = null,
  val cookMinutes: Int? = null,
  val servings: Int? = null,
  val imageUrl: String? = null,
  val pantryMatchPercent: Int = 0,
  val cookCount: Int = 0,
)

@Serializable
data class PlanAlternativesResponse(val alternatives: List<PlanAlternative> = emptyList())

@Serializable
data class ShoppingAddRequest(
  val name: String, val quantity: Double? = null, val unit: String? = null,
  val aisle: String? = null, val source: String = "manual",
)

@Serializable
data class ShoppingUpdateRequest(val checked: Boolean? = null, val name: String? = null, val quantity: Double? = null, val aisle: String? = null)

/** Logged when a user taps "Send list to Amazon / Walmart / Instacart." Free
 *  signal: tells us which vendor users actually pick, average list size,
 *  category mix. Pure analytics, no PII. */
@Serializable
data class VendorHandoffRequest(
  @SerialName("vendor_id") val vendorId: String,
  @SerialName("item_count") val itemCount: Int,
  @SerialName("category_count") val categoryCount: Int? = null,
  @SerialName("estimated_total_cents") val estimatedTotalCents: Int? = null,
)

// Plans
@Serializable
data class Plan(val id: String, val name: String? = null, val recipeIds: List<String>, val createdAt: Long? = null)

@Serializable
data class PlansResponse(val plans: List<Plan>)

@Serializable
data class CreatePlanRequest(val recipeIds: List<String>, val name: String? = null)

@Serializable
data class PlanResponse(val plan: Plan)

// Reviews
@Serializable
data class Review(
  val id: String,
  val userId: String,
  val recipeId: String,
  val ratingPots: Int,
  val tasteRating: Int? = null,
  val easeRating: Int? = null,
  val wouldCookAgain: Boolean? = null,
  val notes: String? = null,
  val isPublic: Boolean = false,
  val photoUrl: String? = null,
  val createdAt: Long = 0L,
)

@Serializable
data class ReviewRequest(
  val recipeId: String, val ratingPots: Int,
  val tasteRating: Int? = null, val easeRating: Int? = null,
  val wouldCookAgain: Boolean? = null, val notes: String? = null,
  val isPublic: Boolean = false, val photoUrl: String? = null,
)

@Serializable
data class ReviewResponse(val review: Review)

@Serializable
data class ReviewFeedResponse(val reviews: List<Review>)

// Billing
@Serializable
data class VerifyPurchaseRequest(
  val purchaseToken: String,
  val productId: String,
  val packageName: String? = null,
  val integrityToken: String? = null,
)

@Serializable
data class VerifyPurchaseResponse(
  val valid: Boolean,
  val entitlement: Entitlement? = null,
)

@Serializable
data class Entitlement(val sku: String, val expiresAt: Long, val autoRenewing: Boolean)

@Serializable
data class EntitlementResponse(
  val active: Boolean = false,
  val sku: String? = null,
  val expiresAt: Long? = null,
  val autoRenewing: Boolean? = null,
)

// ---------- Pantry ----------
@Serializable
data class PantryItem(
  val id: String,
  val name: String,
  val category: String? = null,
  val quantity: Double? = null,
  val unit: String? = null,
  val expiresAt: String? = null,
  val createdAt: Long = 0L,
)

@Serializable
data class PantryListResponse(val items: List<PantryItem>)

@Serializable
data class PantryAddRequest(
  val name: String,
  val category: String? = null,
  val quantity: Double? = null,
  val unit: String? = null,
  val expiresAt: String? = null,
)

@Serializable
data class PantryAddResponse(val ok: Boolean, val id: String, val expiresAt: String? = null)

@Serializable
data class PantryBulkRequest(val items: List<PantryAddRequest>)

@Serializable
data class PantryBulkAdded(val id: String, val name: String, val expiresAt: String? = null)

@Serializable
data class PantryBulkResponse(val ok: Boolean, val added: List<PantryBulkAdded>)

@Serializable
data class PantryUpdateRequest(
  val name: String? = null,
  val category: String? = null,
  val quantity: Double? = null,
  val unit: String? = null,
  val expiresAt: String? = null,
)

// ---------- Barcode ----------
@Serializable
data class BarcodeLookupRequest(val barcode: String)

@Serializable
data class BarcodeNutrition(
  val calories: Double? = null,
  val protein_g: Double? = null,
  val carbs_g: Double? = null,
  val fat_g: Double? = null,
  val fiber_g: Double? = null,
  val sodium_mg: Int? = null,
  val per: String? = null,
)

@Serializable
data class BarcodeProduct(
  val source: String,
  val name: String,
  val brand: String? = null,
  val category: String,
  val imageUrl: String? = null,
  val quantityLabel: String? = null,
  val suggestedExpiryDays: Int,
  val nutrition: BarcodeNutrition? = null,
)

@Serializable
data class BarcodeLookupResponse(
  val ok: Boolean,
  val product: BarcodeProduct? = null,
  val cached: Boolean = false,
  val error: String? = null,
)

// ---------- Receipt ----------
@Serializable
data class ReceiptItem(
  val name: String,
  val category: String,
  val quantity: Double,
  val unit: String? = null,
  @SerialName("price_usd") val priceUsd: Double? = null,
  val confidence: String,
  @SerialName("suggested_expires_days") val suggestedExpiresDays: Int? = null,
)

@Serializable
data class ReceiptResponse(
  val ok: Boolean,
  val items: List<ReceiptItem> = emptyList(),
  val store: String? = null,
  val total: Double? = null,
  val currency: String? = null,
  val dailyLimit: Int? = null,
  val error: String? = null,
)

// ---------- Waste ----------
@Serializable
data class WasteLogRequest(
  val itemId: String? = null,
  val name: String,
  val category: String,
  val quantity: Double? = null,
  val unit: String? = null,
  val action: String,  // cooked|consumed|donated|wasted|expired
)

@Serializable
data class WasteLogResponse(val ok: Boolean, val id: String, val estCostUsd: Double)

@Serializable
data class WasteSummary(
  val range: String,
  val savedUsd: Double,
  val wastedUsd: Double,
  val netUsd: Double,
  val cookedCount: Int,
  val wastedCount: Int,
  val byCategory: Map<String, Double>,
  val since: Long,
)

// ---------- Nutrition ----------
@Serializable
data class RecipeNutrition(
  val calories: Int,
  @SerialName("protein_g") val proteinG: Double,
  @SerialName("carbs_g") val carbsG: Double,
  @SerialName("fat_g") val fatG: Double,
  @SerialName("fiber_g") val fiberG: Double? = null,
  @SerialName("sodium_mg") val sodiumMg: Int? = null,
  val confidence: String? = null,
)

@Serializable
data class NutritionResponse(val ok: Boolean, val nutrition: RecipeNutrition, val cached: Boolean)

// ---------- Substitutions ----------
@Serializable
data class Substitute(val to: String, val ratio: String, val notes: String = "")

@Serializable
data class SubstitutionsResponse(
  val ok: Boolean,
  val ingredient: String,
  val subs: List<Substitute>,
  val source: String,
)

// ---------- Beta / Analytics ----------
@Serializable
data class BetaFeedbackRequest(
  val kind: String,                // bug | idea | praise | other
  val title: String,
  val body: String? = null,
  val route: String? = null,
  val appVersion: String? = null,
  val device: String? = null,
  val logs: String? = null,
  val severity: String? = null,    // low | med | high | crash
)

@Serializable
data class BetaFeedbackResponse(val ok: Boolean = true, val id: String? = null)

@Serializable
data class AnalyticsEvent(
  val name: String,
  val route: String? = null,
  val props: kotlinx.serialization.json.JsonObject? = null,
  val ts: Long = 0L,
)

@Serializable
data class AnalyticsBatch(
  val sessionId: String,
  val appVersion: String,
  val events: List<AnalyticsEvent>,
)

@Serializable
data class AnalyticsAck(val ok: Boolean = true, val accepted: Int = 0)

@Serializable
data class ActivityItem(
  val kind: String,                // cooked | saved
  val recipeId: String,
  val title: String,
  val cuisine: String? = null,
  val at: Long,
)

@Serializable
data class TrendingItem(
  val recipeId: String,
  val title: String,
  val cuisine: String? = null,
  val cooks: Int,
)

@Serializable
data class ActivityResponse(
  val items: List<ActivityItem> = emptyList(),
  val trending: List<TrendingItem> = emptyList(),
)

@Serializable
data class CommunityReview(
  val id: String,
  val ratingPots: Int = 0,
  val notes: String? = null,
  val photoUrl: String? = null,
  val createdAt: Long = 0L,
  val isOwn: Boolean = false,
  val author: String = "anonymous",
  val recipeId: String,
  val recipeTitle: String,
  val recipeCuisine: String? = null,
  val recipeImage: String? = null,
)

@Serializable
data class CommunityReviewsResponse(val reviews: List<CommunityReview> = emptyList())

// ---------- Preferences / Taste ----------
@Serializable
data class PreferencesDto(
  val cuisines: List<String> = emptyList(),
  val avoid: List<String> = emptyList(),
  val diet: String = "none",
  val allergens: List<String> = emptyList(),
  val heat: Int = 1,
  val adventure: Int = 1,
  val onboarded: Boolean = false,
  val updatedAt: Long = 0L,
)

@Serializable
data class PutPreferencesRequest(
  val cuisines: List<String> = emptyList(),
  val avoid: List<String> = emptyList(),
  val diet: String = "none",
  val allergens: List<String> = emptyList(),
  val heat: Int = 1,
  val adventure: Int = 1,
  val onboarded: Boolean = false,
)

@Serializable
data class CuisineScore(val name: String = "", val score: Double = 0.0)

@Serializable
data class IngredientScore(val name: String = "", val score: Double = 0.0)

@Serializable
data class TasteProfile(
  val totalSignals: Int = 0,
  val topCuisines: List<CuisineScore> = emptyList(),
  val topIngredients: List<IngredientScore> = emptyList(),
  val avoidedIngredients: List<IngredientScore> = emptyList(),
  val complexityAvgMinutes: Int = 0,
  val savedCount: Int = 0,
  val cookedCount: Int = 0,
  val dismissedCount: Int = 0,
)

// ---------- Meal Prep (Pro) ----------
@Serializable
data class MealPrepRequest(
  val targetCalories: Double,
  val targetProtein: Double,
  val servingsPerDay: Int,
  val recipesPerWeek: Int,
  val weeks: Int,
)

@Serializable
data class MealPrepRecipe(
  val id: String,
  val title: String,
  val cuisine: String? = null,
  val cookMinutes: Int? = null,
  val prepMinutes: Int? = null,
  val servings: Int? = null,
  val caloriesPerServing: Int? = null,
  val proteinPerServing: Double? = null,
  val batchCookable: Boolean? = null,
  val score: Double? = null,
)

@Serializable
data class MealPrepWeekTotals(
  val weeklyCalories: Int = 0,
  val weeklyProtein: Int = 0,
  val projectedDailyCalories: Int = 0,
  val projectedDailyProtein: Int = 0,
  val calorieDeviation: Int = 0,
  val proteinDeviation: Int = 0,
  val withinTolerance: Boolean = false,
)

@Serializable
data class MealPrepWeek(
  val index: Int,
  val recipes: List<MealPrepRecipe> = emptyList(),
  val totals: MealPrepWeekTotals? = null,
)

@Serializable
data class MealPrepShoppingItem(
  val name: String,
  val canonical: String,
  val quantity: Double? = null,
  val unit: String? = null,
  val aisle: String? = null,
  val recipeCount: Int = 1,
)

// ---------- Recipe Submissions ----------
@Serializable
data class PhotoUploadRequest(val image: String)   // data:image/jpeg;base64,...

@Serializable
data class PhotoUploadResponse(val ok: Boolean = true, val url: String = "", val key: String = "")

// Contribute-photo (existing recipe): user submits a photo that, if approved,
// becomes the recipe's canonical image and earns the contributor photo credit.
@Serializable
data class ContributeRecipePhotoRequest(val image: String)   // data:image/jpeg;base64,...

@Serializable
data class ContributeRecipePhotoResponse(
  val ok: Boolean = true,
  val submissionId: String = "",
  val status: String = "pending",
)

@Serializable
data class SubmitIngredient(
  val name: String,
  val quantity: Double? = null,
  val unit: String? = null,
  val aisle: String? = null,
)

@Serializable
data class SubmitStep(val text: String, val timer_seconds: Int? = null)

@Serializable
data class SubmitRecipeRequest(
  val title: String,
  val cuisine: String? = null,
  val description: String? = null,
  val prepMinutes: Int? = null,
  val cookMinutes: Int? = null,
  val servings: Int? = null,
  val ingredients: List<SubmitIngredient>,
  val steps: List<SubmitStep>,
  val imageUrl: String,
)

@Serializable
data class DupInfo(val id: String, val title: String)

@Serializable
data class SubmitRecipeResponse(
  val ok: Boolean = true,
  val id: String = "",
  val status: String = "pending",
  val dupOf: DupInfo? = null,
)

@Serializable
data class MySubmission(
  val id: String,
  val title: String,
  val cuisine: String? = null,
  @SerialName("image_url") val imageUrl: String? = null,
  val status: String = "pending",
  @SerialName("reject_reason") val rejectReason: String? = null,
  @SerialName("dup_of_recipe_id") val dupOfRecipeId: String? = null,
  @SerialName("approved_as") val approvedAs: String? = null,
  @SerialName("created_at") val createdAt: Long = 0L,
  @SerialName("reviewed_at") val reviewedAt: Long? = null,
)

@Serializable
data class MySubmissionsResponse(val submissions: List<MySubmission> = emptyList())

// ---------- Pro Photo-to-Recipe ----------
@Serializable
data class ExtractRecipeRequest(
  @SerialName("photo_base64") val photoBase64: String,   // data:image/jpeg;base64,... OR raw base64
)

@Serializable
data class ExtractedIngredient(
  @SerialName("canonical_name") val canonicalName: String,
  val quantity: Double? = null,
  val unit: String? = null,
)

@Serializable
data class ExtractRecipeResponse(
  val ok: Boolean = true,
  val extractable: Boolean = true,
  val title: String = "",
  val cuisine: String? = null,
  @SerialName("content_type") val contentType: String = "food",
  val servings: Int? = null,
  @SerialName("time_minutes") val timeMinutes: Int? = null,
  val ingredients: List<ExtractedIngredient> = emptyList(),
  val steps: List<String> = emptyList(),
  // Pro gate / quota error pass-through
  val error: String? = null,
  val upsell: Boolean? = null,
)

@Serializable
data class StructuredIngredient(
  @SerialName("canonical_name") val canonicalName: String,
  val quantity: Double? = null,
  val unit: String? = null,
)

@Serializable
data class StructuredSubmitRequest(
  val title: String,
  val cuisine: String? = null,
  @SerialName("content_type") val contentType: String = "food",
  val servings: Int? = null,
  @SerialName("time_minutes") val timeMinutes: Int? = null,
  val ingredients: List<StructuredIngredient>,
  val steps: List<String>,
  val imageUrl: String? = null,
)

@Serializable
data class StructuredSubmitResponse(
  val ok: Boolean = true,
  val id: String = "",
  val status: String = "pending",
  @SerialName("contentType") val contentType: String = "food",
  val dupOf: DupInfo? = null,
  val url: String? = null,
)

// ---------- Saved / Favorites ----------
@Serializable
data class SavedRecipe(
  val id: String,
  val title: String,
  val cuisine: String? = null,
  val prepMinutes: Int? = null,
  val cookMinutes: Int? = null,
  val servings: Int? = null,
  val avgRating: Double? = null,
  val totalRatings: Int? = null,
  val cookCount: Int = 0,
  val imageUrl: String? = null,
  val pantryMatchPercent: Int = 0,
  val missingCount: Int = 0,
  val savedAt: Long = 0L,
  // "food" (default) / "cocktail" / "mocktail" — drives Library accordion grouping.
  @SerialName("content_type") val contentType: String = "food",
)

@Serializable
data class SavedResponse(val saved: List<SavedRecipe> = emptyList())

@Serializable
data class ReshopRequest(val recipeId: String)

@Serializable
data class ReshopResponse(val ok: Boolean = true, val added: Int = 0)

@Serializable
data class MealPrepResponse(
  val ok: Boolean? = null,
  val tier: String? = null,
  val upgrade: String? = null,
  val error: String? = null,
  val preview: MealPrepWeek? = null,
  val weeks: List<MealPrepWeek> = emptyList(),
  val shoppingList: List<MealPrepShoppingItem> = emptyList(),
)

// ---------- Search ----------
@Serializable
data class SearchResponse(
  val results: List<SearchHit> = emptyList(),
  val total: Int = 0,
)

@Serializable
data class SearchHit(
  val id: String,
  val title: String,
  val cuisine: String? = null,
  @SerialName("image_url") val imageUrl: String? = null,
  @SerialName("content_type") val contentType: String = "food",
  @SerialName("source_year") val sourceYear: Int? = null,
  @SerialName("source_book") val sourceBook: String? = null,
  @SerialName("glass_type") val glassType: String? = null,
  @SerialName("abv_percent") val abvPercent: Double? = null,
  @SerialName("is_historic") val isHistoric: Boolean = false,
  @SerialName("is_alcoholic") val isAlcoholic: Boolean = false,
)

@kotlinx.serialization.Serializable
data class CreateGameRequest(
  @kotlinx.serialization.SerialName("menu_id") val menuId: String,
)

@kotlinx.serialization.Serializable
data class CreateGameResponse(
  val code: String,
  @kotlinx.serialization.SerialName("ws_path") val wsPath: String,
  @kotlinx.serialization.SerialName("player_url") val playerUrl: String? = null,
)
