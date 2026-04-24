package app.pantrie.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import app.pantrie.crypto.KeystoreKeyManager
import app.pantrie.data.entities.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import javax.inject.Singleton

@Database(
  entities = [
    PantryItemEntity::class,
    RecipeCacheEntity::class,
    InteractionEntity::class,
    ShoppingItemEntity::class,
    ReviewDraftEntity::class,
  ],
  version = 1,
  exportSchema = false,
)
abstract class PantrieDatabase : RoomDatabase() {
  abstract fun pantryDao(): PantryDao
  abstract fun recipeDao(): RecipeDao
  abstract fun interactionDao(): InteractionDao
  abstract fun shoppingDao(): ShoppingDao
  abstract fun reviewDao(): ReviewDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

  @Provides
  @Singleton
  fun provideDatabase(
    @ApplicationContext ctx: Context,
    keys: KeystoreKeyManager,
  ): PantrieDatabase {
    System.loadLibrary("sqlcipher")
    return buildDatabase(ctx, keys, allowReset = true)
  }

  private fun buildDatabase(
    ctx: Context,
    keys: KeystoreKeyManager,
    allowReset: Boolean,
  ): PantrieDatabase {
    val passphrase = keys.getDatabasePassphrase()
    // clearPassphrase=true zeroes the byte array after SQLCipher consumes it.
    val factory = SupportOpenHelperFactory(passphrase, null, /* clearPassphrase = */ true)
    val db = Room.databaseBuilder(ctx, PantrieDatabase::class.java, "pantrie.db")
      .openHelperFactory(factory)
      .fallbackToDestructiveMigration()
      .build()

    // Validate open. If this fails, the DEK does not match the on-disk DB — likely a
    // corrupted state after Keystore invalidation. Reset and rebuild (best effort).
    return runCatching {
      db.openHelper.writableDatabase.query("PRAGMA cipher_version;").use { /* ok */ }
      db
    }.getOrElse {
      if (!allowReset) throw it
      db.close()
      File(ctx.getDatabasePath("pantrie.db").path).delete()
      File(ctx.getDatabasePath("pantrie.db-wal").path).delete()
      File(ctx.getDatabasePath("pantrie.db-shm").path).delete()
      keys.resetDatabaseKey()
      buildDatabase(ctx, keys, allowReset = false)
    }
  }

  @Provides fun providePantryDao(db: PantrieDatabase) = db.pantryDao()
  @Provides fun provideRecipeDao(db: PantrieDatabase) = db.recipeDao()
  @Provides fun provideInteractionDao(db: PantrieDatabase) = db.interactionDao()
  @Provides fun provideShoppingDao(db: PantrieDatabase) = db.shoppingDao()
  @Provides fun provideReviewDao(db: PantrieDatabase) = db.reviewDao()
}
