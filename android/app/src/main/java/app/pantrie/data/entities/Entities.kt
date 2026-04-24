package app.pantrie.data.entities

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ---- Entities ----

@Entity(tableName = "pantry_item")
data class PantryItemEntity(
  @PrimaryKey val id: String,
  val name: String,
  val category: String?,
  val quantity: Double?,
  val unit: String?,
  val expiresAt: String?,
  val createdAt: Long,
)

@Entity(tableName = "recipe_cache")
data class RecipeCacheEntity(
  @PrimaryKey val id: String,
  val title: String,
  val cuisine: String?,
  val description: String?,
  val skillLevel: String?,
  val prepMinutes: Int,
  val cookMinutes: Int,
  val servings: Int,
  val avgRating: Double,
  val totalRatings: Int,
  val cachedAt: Long,
)

@Entity(tableName = "interaction")
data class InteractionEntity(
  @PrimaryKey val recipeId: String,
  val status: String,                  // saved | planned | cooked | dismissed
  val dismissReason: String?,
  val createdAt: Long,
)

@Entity(tableName = "shopping_item")
data class ShoppingItemEntity(
  @PrimaryKey val id: String,
  val name: String,
  val quantity: Double?,
  val unit: String?,
  val aisle: String?,
  val checked: Boolean,
  val source: String,                  // manual | plan | suggestion
  val recipeIdRef: String?,
  val createdAt: Long,
)

@Entity(tableName = "review_draft")
data class ReviewDraftEntity(
  @PrimaryKey val id: String,
  val recipeId: String,
  val ratingPots: Int,
  val tasteRating: Int?,
  val easeRating: Int?,
  val wouldCookAgain: Boolean?,
  val notes: String?,
  val isPublic: Boolean,
  val photoPath: String?,              // local encrypted file path
  val createdAt: Long,
  val syncedAt: Long?,
)

// ---- DAOs ----

@Dao
interface PantryDao {
  @Query("SELECT * FROM pantry_item ORDER BY createdAt DESC")
  fun observeAll(): Flow<List<PantryItemEntity>>

  @Query("SELECT * FROM pantry_item")
  suspend fun getAll(): List<PantryItemEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(items: List<PantryItemEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(item: PantryItemEntity)

  @Query("DELETE FROM pantry_item WHERE id = :id")
  suspend fun delete(id: String)

  @Query("DELETE FROM pantry_item")
  suspend fun clear()
}

@Dao
interface RecipeDao {
  @Query("SELECT * FROM recipe_cache WHERE id = :id")
  suspend fun get(id: String): RecipeCacheEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(recipe: RecipeCacheEntity)

  @Query("DELETE FROM recipe_cache")
  suspend fun clear()
}

@Dao
interface InteractionDao {
  @Query("SELECT * FROM interaction WHERE status = :status")
  fun observeByStatus(status: String): Flow<List<InteractionEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(interaction: InteractionEntity)

  @Query("DELETE FROM interaction WHERE recipeId = :recipeId")
  suspend fun delete(recipeId: String)
}

@Dao
interface ShoppingDao {
  @Query("SELECT * FROM shopping_item ORDER BY aisle, createdAt")
  fun observeAll(): Flow<List<ShoppingItemEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(item: ShoppingItemEntity)

  @Query("UPDATE shopping_item SET checked = :checked WHERE id = :id")
  suspend fun setChecked(id: String, checked: Boolean)

  @Query("DELETE FROM shopping_item WHERE id = :id")
  suspend fun delete(id: String)
}

@Dao
interface ReviewDao {
  @Query("SELECT * FROM review_draft ORDER BY createdAt DESC")
  fun observeAll(): Flow<List<ReviewDraftEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(review: ReviewDraftEntity)

  @Query("DELETE FROM review_draft WHERE id = :id")
  suspend fun delete(id: String)
}
