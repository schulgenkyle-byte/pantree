package app.pantrie.crypto

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256-GCM-HKDF-4KB encrypted files (AndroidX Security Crypto / Tink).
 * Scan photos and review photos live here.
 */
@Singleton
class EncryptedFileStore @Inject constructor(@ApplicationContext private val ctx: Context) {

  private val masterKey: MasterKey by lazy {
    MasterKey.Builder(ctx, "pantrie_master_key")
      .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
      .build()
  }

  fun savePhoto(subdir: String, name: String, bytes: ByteArray): File {
    val dir = File(ctx.filesDir, subdir).apply { mkdirs() }
    val file = File(dir, name)
    if (file.exists()) file.delete()  // EncryptedFile refuses to overwrite
    val enc = EncryptedFile.Builder(
      ctx, file, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
    ).build()
    enc.openFileOutput().use { it.write(bytes) }
    return file
  }

  fun readPhoto(subdir: String, name: String): ByteArray? {
    val file = File(File(ctx.filesDir, subdir), name)
    if (!file.exists()) return null
    val enc = EncryptedFile.Builder(
      ctx, file, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
    ).build()
    return enc.openFileInput().use { it.readBytes() }
  }

  fun delete(subdir: String, name: String): Boolean =
    File(File(ctx.filesDir, subdir), name).delete()

  fun deleteAll(subdir: String) {
    File(ctx.filesDir, subdir).deleteRecursively()
  }
}
