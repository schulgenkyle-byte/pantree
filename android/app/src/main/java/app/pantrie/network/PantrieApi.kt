package app.pantrie.network

import app.pantrie.network.dto.*
import retrofit2.http.*

interface PantrieApi {
  @POST("auth/nonce")
  suspend fun authNonce(): NonceResponse

  @POST("auth/google-exchange")
  suspend fun googleExchange(@Body req: GoogleExchangeRequest): SessionResponse

  @POST("auth/dev-token")
  suspend fun devToken(@Header("x-dev-key") devKey: String, @Body req: DevTokenRequest): SessionResponse

  @POST("auth/refresh")
  suspend fun refresh(@Body req: RefreshRequest): SessionResponse

  @POST("auth/logout")
  suspend fun logout(@Body req: LogoutRequest): GenericOk

  @POST("auth/logout-all")
  suspend fun logoutAll(): GenericOk

  @POST("auth/reauth")
  suspend fun reauth(@Body req: ReauthRequest): ReauthResponse

  @GET("health")
  suspend fun health(): GenericOk

  @POST("scan")
  suspend fun scan(@Body req: ScanRequest): ScanResponse

  @GET("me")
  suspend fun me(): User

  @GET("me/home")
  suspend fun homeStats(): HomeStats

  @PATCH("me")
  suspend fun updateMe(@Body req: UpdateMeRequest): GenericOk

  @HTTP(method = "DELETE", path = "me", hasBody = true)
  suspend fun deleteAccount(@Body req: DeleteAccountRequest): GenericOk

  @POST("me/export")
  suspend fun exportMe(): kotlinx.serialization.json.JsonObject

  @GET("recipes/deck")
  suspend fun deck(
    @Query("adventurous") adventurous: Int? = null,
    @Query("filter") filter: String? = null,
    @Query("content_type") contentType: String? = null,
    @Query("require_photo") requirePhoto: Int? = null,
  ): DeckResponse

  @GET("recipes/search")
  suspend fun searchRecipes(
    @Query("q") query: String,
    @Query("content_type") contentType: String? = null,
    @Query("alcoholic") alcoholic: Int? = null,
    @Query("limit") limit: Int = 50,
  ): SearchResponse

  @GET("recipes/{id}")
  suspend fun recipe(@Path("id") id: String): Recipe

  @POST("interactions")
  suspend fun interact(@Body req: InteractRequest): InteractResponse

  @POST("interactions/undo")
  suspend fun undoCook(@Body req: UndoCookRequest): GenericOk

  @GET("shopping")
  suspend fun shopping(): ShoppingResponse

  @GET("shopping/smart")
  suspend fun shoppingSmart(): SmartShoppingResponse

  @POST("shopping")
  suspend fun addShopping(@Body item: ShoppingAddRequest): ShoppingItem

  @PATCH("shopping/{id}")
  suspend fun updateShopping(@Path("id") id: String, @Body req: ShoppingUpdateRequest): GenericOk

  @DELETE("shopping/{id}")
  suspend fun deleteShopping(@Path("id") id: String): GenericOk

  @POST("shopping/vendor-handoff")
  suspend fun shoppingVendorHandoff(@Body req: VendorHandoffRequest): GenericOk

  @GET("plans")
  suspend fun plans(): PlansResponse

  @POST("plans")
  suspend fun createPlan(@Body req: CreatePlanRequest): PlanResponse

  @GET("plans/propose")
  suspend fun proposePlan(@Query("slots") slots: Int = 7): PlanProposalResponse

  @POST("plans/alternatives")
  suspend fun planAlternatives(@Body req: PlanAlternativesRequest): PlanAlternativesResponse

  @DELETE("plans/{id}")
  suspend fun deletePlan(@Path("id") id: String): GenericOk

  @POST("reviews")
  suspend fun review(@Body req: ReviewRequest): ReviewResponse

  @GET("reviews")
  suspend fun reviewFeed(): ReviewFeedResponse

  @DELETE("reviews/{id}")
  suspend fun deleteReview(@Path("id") id: String): GenericOk

  @POST("reviews/{id}/report")
  suspend fun reportReview(@Path("id") id: String, @Body body: Map<String, String>): GenericOk

  @POST("follows/{userId}")
  suspend fun follow(@Path("userId") userId: String): GenericOk

  @DELETE("follows/{userId}")
  suspend fun unfollow(@Path("userId") userId: String): GenericOk

  @POST("blocks/{userId}")
  suspend fun block(@Path("userId") userId: String): GenericOk

  @DELETE("blocks/{userId}")
  suspend fun unblock(@Path("userId") userId: String): GenericOk

  @POST("billing/verify")
  suspend fun verifyPurchase(@Body req: VerifyPurchaseRequest): VerifyPurchaseResponse

  @GET("me/entitlement")
  suspend fun entitlement(): EntitlementResponse

  // Dev-only — server returns 404 unless ENVIRONMENT=dev. Used by the Settings debug
  // toggle to flip the calling user's Pro state without going through Play Billing.
  @POST("me/debug/grant-pro")
  suspend fun debugGrantPro(): EntitlementResponse

  @POST("me/debug/revoke-pro")
  suspend fun debugRevokePro(): EntitlementResponse

  // ---------- Mystery Nights multiplayer ----------
  @POST("games/create")
  suspend fun createGame(@Body req: app.pantrie.network.dto.CreateGameRequest): app.pantrie.network.dto.CreateGameResponse

  @GET("scan/status")
  suspend fun scanStatus(): ScanStatusResponse

  // ---------- Pantry ----------
  @GET("pantry")
  suspend fun pantry(): PantryListResponse

  @POST("pantry")
  suspend fun addPantryItem(@Body req: PantryAddRequest): PantryAddResponse

  @POST("pantry/bulk")
  suspend fun addPantryItemsBulk(@Body req: PantryBulkRequest): PantryBulkResponse

  @PATCH("pantry/{id}")
  suspend fun updatePantryItem(@Path("id") id: String, @Body req: PantryUpdateRequest): GenericOk

  @DELETE("pantry/{id}")
  suspend fun deletePantryItem(@Path("id") id: String): GenericOk

  // ---------- Barcode ----------
  @POST("barcode/lookup")
  suspend fun lookupBarcode(@Body req: BarcodeLookupRequest): BarcodeLookupResponse

  // ---------- Receipt ----------
  @POST("scan/receipt")
  suspend fun scanReceipt(@Body req: ScanRequest): ReceiptResponse

  // ---------- Waste ----------
  @POST("waste/log")
  suspend fun logWaste(@Body req: WasteLogRequest): WasteLogResponse

  @GET("waste/summary")
  suspend fun wasteSummary(@Query("range") range: String = "week"): WasteSummary

  // ---------- Nutrition ----------
  @GET("recipes/{id}/nutrition")
  suspend fun recipeNutrition(@Path("id") id: String): NutritionResponse

  // ---------- Substitutions ----------
  @GET("substitutions/{ingredient}")
  suspend fun substitutions(@Path("ingredient") ingredient: String): SubstitutionsResponse

  // ---------- Beta / Analytics ----------
  @POST("beta/feedback")
  suspend fun sendFeedback(@Body req: BetaFeedbackRequest): BetaFeedbackResponse

  @POST("events")
  suspend fun sendEvents(@Body batch: AnalyticsBatch): AnalyticsAck

  @GET("beta/activity")
  suspend fun betaActivity(): ActivityResponse

  @GET("community/reviews")
  suspend fun communityReviews(): CommunityReviewsResponse

  // ---------- Preferences / Taste ----------
  @GET("me/preferences")
  suspend fun getPreferences(): PreferencesDto

  @PUT("me/preferences")
  suspend fun putPreferences(@Body req: PutPreferencesRequest): PreferencesDto

  @GET("me/taste")
  suspend fun getTaste(): TasteProfile

  // ---------- Meal Prep ----------
  @POST("plans/meal-prep")
  suspend fun mealPrep(@Body req: MealPrepRequest): MealPrepResponse

  // ---------- Saved / Favorites ----------
  @GET("me/saved")
  suspend fun saved(): SavedResponse

  @POST("interactions/reshop")
  suspend fun reshop(@Body req: ReshopRequest): ReshopResponse

  @HTTP(method = "DELETE", path = "shopping", hasBody = false)
  suspend fun clearShopping(@Query("onlyChecked") onlyChecked: Int? = null): GenericOk

  // ---------- Recipe Submissions ----------
  @POST("submissions/photo")
  suspend fun uploadSubmissionPhoto(@Body req: PhotoUploadRequest): PhotoUploadResponse

  /** Contribute a photo to an EXISTING recipe. If approved, the photo
   *  becomes the recipe's canonical image and the user gets credit. */
  @POST("recipes/{id}/contribute-photo")
  suspend fun contributeRecipePhoto(
    @Path("id") id: String,
    @Body req: app.pantrie.network.dto.ContributeRecipePhotoRequest,
  ): app.pantrie.network.dto.ContributeRecipePhotoResponse

  @POST("submissions/recipe")
  suspend fun submitRecipe(@Body req: SubmitRecipeRequest): SubmitRecipeResponse

  @GET("submissions/mine")
  suspend fun mySubmissions(): MySubmissionsResponse

  // ---------- Pro Photo-to-Recipe ----------
  // Returns retrofit2.Response so we can distinguish 422 (extractable:false, body still
  // valid JSON) from 402 (Pro gate, surface upgrade nudge) without conflating both as
  // HttpException. ViewModel reads response.code() + response.body()/errorBody().
  @POST("me/extract-recipe-from-photo")
  suspend fun extractRecipeFromPhoto(@Body req: ExtractRecipeRequest): retrofit2.Response<ExtractRecipeResponse>

  @POST("me/submit-recipe")
  suspend fun submitStructuredRecipe(@Body req: StructuredSubmitRequest): StructuredSubmitResponse

  // ---------- Link Import (TikTok/YouTube → recipe) ----------
  @POST("api/import/links")
  suspend fun createImportJob(@Body req: app.pantrie.feature.importlinks.CreateImportJobRequest): app.pantrie.feature.importlinks.CreateImportJobResponse

  @GET("api/import/jobs/{id}")
  suspend fun getImportJob(@Path("id") id: String): app.pantrie.feature.importlinks.ImportJobResponse

  @POST("api/import/links/{id}/submit")
  suspend fun submitImportedLink(
    @Path("id") id: String,
    @Body req: app.pantrie.feature.importlinks.SubmitImportedRequest,
  ): app.pantrie.feature.importlinks.SubmitImportedResponse

  @POST("api/import/links/{id}/reject")
  suspend fun rejectImportedLink(@Path("id") id: String): app.pantrie.feature.importlinks.OkResponse

  // ---------- Library: Books → Chapters → Recipes ----------
  @GET("api/library")
  suspend fun getLibrary(): app.pantrie.feature.library.LibraryResponse

  @POST("api/library/books")
  suspend fun createBook(@Body req: app.pantrie.feature.library.CreateBookRequest): app.pantrie.feature.library.Book

  @GET("api/library/books/{id}")
  suspend fun getBook(@Path("id") id: String): app.pantrie.feature.library.BookDetailResponse

  @PUT("api/library/books/{id}")
  suspend fun updateBook(
    @Path("id") id: String,
    @Body req: app.pantrie.feature.library.UpdateBookRequest,
  ): app.pantrie.feature.importlinks.OkResponse

  @DELETE("api/library/books/{id}")
  suspend fun deleteBook(@Path("id") id: String): app.pantrie.feature.importlinks.OkResponse

  @POST("api/library/books/{id}/chapters")
  suspend fun createChapter(
    @Path("id") bookId: String,
    @Body req: app.pantrie.feature.library.CreateChapterRequest,
  ): app.pantrie.feature.library.Chapter

  @PUT("api/library/books/{bookId}/chapters/{chapterId}")
  suspend fun updateChapter(
    @Path("bookId") bookId: String,
    @Path("chapterId") chapterId: String,
    @Body req: app.pantrie.feature.library.UpdateChapterRequest,
  ): app.pantrie.feature.importlinks.OkResponse

  @DELETE("api/library/books/{bookId}/chapters/{chapterId}")
  suspend fun deleteChapter(
    @Path("bookId") bookId: String,
    @Path("chapterId") chapterId: String,
  ): app.pantrie.feature.importlinks.OkResponse

  @POST("api/library/books/{bookId}/chapters/{chapterId}/recipes")
  suspend fun addRecipeToChapter(
    @Path("bookId") bookId: String,
    @Path("chapterId") chapterId: String,
    @Body req: app.pantrie.feature.library.AddRecipeRequest,
  ): app.pantrie.feature.library.AddRecipeResponse

  @DELETE("api/library/books/{bookId}/chapters/{chapterId}/recipes/{recipeId}")
  suspend fun removeRecipeFromChapter(
    @Path("bookId") bookId: String,
    @Path("chapterId") chapterId: String,
    @Path("recipeId") recipeId: String,
  ): app.pantrie.feature.importlinks.OkResponse

  @POST("api/library/books/{id}/fork")
  suspend fun forkBook(@Path("id") id: String): app.pantrie.feature.library.Book

  @POST("api/library/books/{id}/share-token")
  suspend fun mintBookShareToken(@Path("id") id: String): app.pantrie.feature.library.ShareTokenResponse
}
