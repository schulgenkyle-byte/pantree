package app.pantrie.crypto

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hardware-backed Key Encryption Key (KEK) in Android Keystore + a random, KEK-wrapped
 * Data Encryption Key (DEK) persisted to internal storage.
 *
 * The KEK never leaves the Keystore; the DEK is the SQLCipher passphrase. First run generates
 * a random 256-bit DEK; subsequent runs unwrap it. This is stable across relaunches — the
 * earlier implementation produced a different ciphertext every call (fresh random IV per
 * encrypt), which would have bricked the DB on second cold start.
 */
@Singleton
class KeystoreKeyManager @Inject constructor(@ApplicationContext private val ctx: Context) {

  private val dekFile: File by lazy { File(ctx.filesDir, "dek_v1.bin") }

  fun getOrCreateKek(alias: String = DEFAULT_ALIAS): SecretKey {
    val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
    ks.getKey(alias, null)?.let { return it as SecretKey }

    val builder = KeyGenParameterSpec.Builder(
      alias,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
    )
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setKeySize(256)
      .setRandomizedEncryptionRequired(true)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isStrongBoxAvailable()) {
      builder.setIsStrongBoxBacked(true)
    }

    val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
    return try {
      gen.init(builder.build())
      gen.generateKey()
    } catch (e: Exception) {
      // StrongBox not available on this device but we claimed it. Retry without.
      val fallback = KeyGenParameterSpec.Builder(
        alias,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
      )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .setRandomizedEncryptionRequired(true)
        .build()
      gen.init(fallback)
      gen.generateKey()
    }
  }

  /**
   * Returns the SQLCipher passphrase (32 random bytes, KEK-wrapped at rest).
   * Caller MUST zero the returned ByteArray after handing it to SQLCipher
   * (SupportOpenHelperFactory with clearPassphrase=true does this for you).
   */
  @Synchronized
  fun getDatabasePassphrase(): ByteArray {
    if (dekFile.exists()) {
      val wrapped = dekFile.readBytes()
      runCatching { return unwrap(wrapped) }
        .onFailure {
          // Corrupt or KEK-unusable (e.g. post-device-restore). Best we can do is reset.
          dekFile.delete()
        }
    }
    val dek = ByteArray(32).also { SecureRandom().nextBytes(it) }
    val wrapped = wrap(dek)
    // Write atomically
    val tmp = File(dekFile.parentFile, dekFile.name + ".tmp")
    tmp.writeBytes(wrapped)
    if (!tmp.renameTo(dekFile)) {
      tmp.delete()
      dekFile.writeBytes(wrapped)
    }
    return dek
  }

  /**
   * Reset the DEK (and therefore drop the encrypted DB). Call from an error-recovery path
   * when Keystore keys are invalidated (e.g. device restored to factory, lockscreen removed).
   */
  @Synchronized
  fun resetDatabaseKey() {
    dekFile.delete()
    val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
    runCatching { ks.deleteEntry(DEFAULT_ALIAS) }
  }

  private fun wrap(plaintext: ByteArray): ByteArray {
    val key = getOrCreateKek()
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key)
    val iv = cipher.iv
    val ct = cipher.doFinal(plaintext)
    return iv + ct
  }

  private fun unwrap(wrapped: ByteArray): ByteArray {
    require(wrapped.size > GCM_IV_LEN + GCM_TAG_LEN / 8) { "wrapped too short" }
    val key = getOrCreateKek()
    val iv = wrapped.copyOfRange(0, GCM_IV_LEN)
    val ct = wrapped.copyOfRange(GCM_IV_LEN, wrapped.size)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LEN, iv))
    return cipher.doFinal(ct)
  }

  private fun isStrongBoxAvailable(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
      ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

  companion object {
    const val KEYSTORE = "AndroidKeyStore"
    const val DEFAULT_ALIAS = "pantrie_kek_v1"
    private const val GCM_IV_LEN = 12
    private const val GCM_TAG_LEN = 128
  }
}
