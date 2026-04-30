package app.pantrie.feature.library

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Book(
  val id: String,
  val slug: String,
  val title: String,
  val description: String? = null,
  @SerialName("cover_image_url") val coverImageUrl: String? = null,
  val visibility: String = "private",
  @SerialName("is_standard") val isStandard: Int = 0,
  @SerialName("standard_kind") val standardKind: String? = null,
  val position: Int = 0,
  @SerialName("fork_of_id") val forkOfId: String? = null,
  @SerialName("recipe_count") val recipeCount: Int = 0,
  @SerialName("view_count") val viewCount: Int = 0,
  @SerialName("fork_count") val forkCount: Int = 0,
  @SerialName("created_at") val createdAt: Long = 0,
  @SerialName("updated_at") val updatedAt: Long = 0,
)

@Serializable
data class LibraryResponse(val books: List<Book>)

@Serializable
data class CreateBookRequest(
  val title: String,
  val description: String? = null,
  val visibility: String = "private",
)

@Serializable
data class UpdateBookRequest(
  val title: String? = null,
  val description: String? = null,
  val visibility: String? = null,
  @SerialName("cover_image_url") val coverImageUrl: String? = null,
)

@Serializable
data class Chapter(
  val id: String,
  val title: String,
  val position: Int = 0,
  @SerialName("recipe_count") val recipeCount: Int = 0,
  @SerialName("created_at") val createdAt: Long = 0,
  val recipes: List<ChapterRecipe> = emptyList(),
)

@Serializable
data class ChapterRecipe(
  @SerialName("recipe_id") val recipeId: String,
  val position: Int = 0,
  @SerialName("user_note") val userNote: String? = null,
  @SerialName("added_at") val addedAt: Long = 0,
  val title: String,
  val cuisine: String? = null,
  @SerialName("image_url") val imageUrl: String? = null,
  @SerialName("skill_level") val skillLevel: String? = null,
  @SerialName("prep_minutes") val prepMinutes: Int? = null,
  @SerialName("cook_minutes") val cookMinutes: Int? = null,
  val servings: Int? = null,
  @SerialName("created_by_user_id") val createdByUserId: String? = null,
)

@Serializable
data class BookDetailResponse(val book: Book, val chapters: List<Chapter>)

@Serializable
data class CreateChapterRequest(val title: String)

@Serializable
data class UpdateChapterRequest(val title: String? = null, val position: Int? = null)

@Serializable
data class AddRecipeRequest(
  @SerialName("recipe_id") val recipeId: String,
  @SerialName("user_note") val userNote: String? = null,
)

@Serializable
data class AddRecipeResponse(val ok: Boolean, val position: Int? = null)

@Serializable
data class ShareTokenResponse(val token: String, @SerialName("expires_at") val expiresAt: Long)
