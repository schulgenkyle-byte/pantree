package app.pantrie.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import app.pantrie.BuildConfig
import app.pantrie.network.PantrieApi
import app.pantrie.network.dto.GoogleExchangeRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialManagerFlow @Inject constructor(
  private val api: PantrieApi,
  private val tokenStore: TokenStore,
) {

  /**
   * Google sign-in:
   *   1. Ask backend for a one-shot nonce.
   *   2. Pass nonce into GetGoogleIdOption (Google binds it into the ID token).
   *   3. Request a Play Integrity token with the same nonce as requestHash.
   *   4. Exchange everything with the backend, which verifies nonce + integrity + id token locally.
   */
  suspend fun signInWithGoogle(activity: Activity): Result<String> = runCatching {
    if (BuildConfig.DEBUG) android.util.Log.i("CredFlow", "signInWithGoogle: requesting nonce")
    val nonceResp = api.authNonce()
    val nonce = nonceResp.nonce
    if (BuildConfig.DEBUG) android.util.Log.i("CredFlow", "signInWithGoogle: got nonce, opening picker")

    val credentialManager = CredentialManager.create(activity)
    // GetSignInWithGoogleOption is the right API for an explicit "Sign in with Google" button —
    // always renders the picker, never hangs on Android 14+ when auto-select can't decide.
    // GetGoogleIdOption (with setAutoSelectEnabled) is for SILENT/zero-tap re-auth flows only.
    val googleOption = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_SERVER_CLIENT_ID)
      .setNonce(nonce)
      .build()

    val request = GetCredentialRequest.Builder().addCredentialOption(googleOption).build()
    // 30-second cap — if the Credential Manager bottom sheet fails to render
    // (rare Android 14+ bug), the user gets an Error state instead of an infinite spinner.
    val result = kotlinx.coroutines.withTimeout(30_000L) {
      credentialManager.getCredential(activity, request)
    }
    if (BuildConfig.DEBUG) android.util.Log.i("CredFlow", "signInWithGoogle: picker returned, extracting id token")

    val cred = result.credential
    val idToken = when {
      cred is GoogleIdTokenCredential -> cred.idToken
      cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ->
        GoogleIdTokenCredential.createFrom(cred.data).idToken
      else -> error("Unexpected credential type: ${cred.type}")
    }
    if (BuildConfig.DEBUG) android.util.Log.i("CredFlow", "signInWithGoogle: requesting integrity token")

    // Integrity token bound to the SAME nonce so the backend can cross-check.
    val integrityToken = getIntegrityToken(activity, nonce)
    if (BuildConfig.DEBUG) android.util.Log.i("CredFlow", "signInWithGoogle: exchanging with backend")

    val resp = api.googleExchange(GoogleExchangeRequest(idToken, nonce, integrityToken))
    tokenStore.saveRefreshToken(resp.refreshToken)
    tokenStore.saveUserId(resp.userId)
    tokenStore.setAccess(resp.accessToken, resp.expiresAt)
    if (BuildConfig.DEBUG) android.util.Log.i("CredFlow", "signInWithGoogle: SUCCESS userId=${resp.userId}")
    resp.userId
  }.onFailure { e ->
    // Class name only — never the user-facing message (which can include emails / userIds
    // returned in upstream errors). Swallow throwable in release to keep stack frames out
    // of crash reporters that aggregate by message.
    if (BuildConfig.DEBUG) {
      android.util.Log.e("CredFlow", "signInWithGoogle FAILED: ${e::class.simpleName}: ${e.message}", e)
    } else {
      android.util.Log.e("CredFlow", "signInWithGoogle FAILED: ${e::class.simpleName}")
    }
  }

  /**
   * Issue a Play Integrity Standard token bound to the server-supplied nonce.
   * The request-hash we send is SHA-256(nonce) — Google allows up to 500 chars
   * but hashing keeps it consistent and small.
   */
  private suspend fun getIntegrityToken(ctx: Context, nonce: String): String? {
    val projectNumber = BuildConfig.CLOUD_PROJECT_NUMBER
    if (projectNumber == 0L) return null
    return runCatching {
      val manager = IntegrityManagerFactory.createStandard(ctx)
      val provider = manager.prepareIntegrityToken(
        PrepareIntegrityTokenRequest.builder()
          .setCloudProjectNumber(projectNumber)
          .build(),
      ).await()
      val hash = MessageDigest.getInstance("SHA-256").digest(nonce.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
      provider.request(
        StandardIntegrityTokenRequest.builder()
          .setRequestHash(hash)
          .build(),
      ).await().token()
    }.getOrNull()
  }

  /**
   * Fresh Google re-auth (e.g. before account deletion). Returns a server-signed reauth token.
   */
  suspend fun freshReauthToken(activity: Activity): String? = runCatching {
    val nonceResp = api.authNonce()
    val nonce = nonceResp.nonce

    val credentialManager = CredentialManager.create(activity)
    val googleOption = GetGoogleIdOption.Builder()
      .setServerClientId(BuildConfig.GOOGLE_SERVER_CLIENT_ID)
      .setFilterByAuthorizedAccounts(true)
      .setAutoSelectEnabled(false)
      .setNonce(nonce)
      .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(googleOption).build()
    val result = credentialManager.getCredential(activity, request)
    val cred = result.credential
    val idToken = when {
      cred is GoogleIdTokenCredential -> cred.idToken
      cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ->
        GoogleIdTokenCredential.createFrom(cred.data).idToken
      else -> error("Unexpected credential type: ${cred.type}")
    }
    api.reauth(app.pantrie.network.dto.ReauthRequest(idToken, nonce)).reauthToken
  }.getOrNull()

  suspend fun signOut(activity: Activity) {
    val refresh = tokenStore.getRefreshToken()
    if (refresh != null) runCatching {
      api.logout(app.pantrie.network.dto.LogoutRequest(refresh))
    }
    tokenStore.clear()
    runCatching { CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest()) }
  }
}
