package app.pantrie.feature.auth

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.BuildConfig
import app.pantrie.auth.CredentialManagerFlow
import app.pantrie.auth.TokenStore
import app.pantrie.network.PantrieApi
import app.pantrie.network.dto.DevTokenRequest
import app.pantrie.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LoginUiState {
  data object Idle : LoginUiState
  data object Loading : LoginUiState
  data class Error(val message: String) : LoginUiState
  data object LoggedIn : LoginUiState
}

@HiltViewModel
class LoginViewModel @Inject constructor(
  private val api: PantrieApi,
  private val tokenStore: TokenStore,
  private val credentialFlow: CredentialManagerFlow,
) : ViewModel() {

  private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
  val state = _state.asStateFlow()

  init {
    // If we already have a refresh token (returning user) skip the login screen.
    if (tokenStore.getRefreshToken() != null) {
      _state.value = LoginUiState.LoggedIn
    }
  }

  // Re-entrancy guard. Two getCredential() calls in flight at once cause the
  // OS to cancel the older one — and that cancellation surfaces as the system
  // toast "SIGN IN REQUEST CANCELLED BY SPEAKEATER" before the second picker
  // renders. A double-tap on the button OR a stale recomposition firing the
  // onClick twice was the source. Single-flight prevents both.
  private var signInJob: kotlinx.coroutines.Job? = null

  /** Real Google sign-in via Credential Manager. This is the production path. */
  fun signInWithGoogle(activity: Activity) {
    if (signInJob?.isActive == true) return
    signInJob = viewModelScope.launch {
      _state.value = LoginUiState.Loading
      credentialFlow.signInWithGoogle(activity)
        .onSuccess { _state.value = LoginUiState.LoggedIn }
        .onFailure { e ->
          // Swallow user-cancellations silently — they tapped Back / outside
          // the picker, which is not an error worth surfacing as red text.
          val msg = e.message?.lowercase().orEmpty()
          val userCancelled = msg.contains("cancel") || msg.contains("user closed") ||
            e::class.simpleName?.contains("Cancellation") == true
          if (userCancelled) {
            _state.value = LoginUiState.Idle
          } else {
            _state.value = LoginUiState.Error(e.message ?: "Sign-in failed")
          }
        }
    }
  }

  /**
   * Debug-only fallback. Only available when BuildConfig.DEBUG is true (debug builds).
   * Release builds (Play Store) hide this entirely. The backend also rejects dev tokens
   * unless the dev-key header matches a value only present in the staging Worker secrets.
   */
  fun devLogin() {
    // Belt-and-suspenders: refuse dev-login unless THIS build is the .dev
    // application variant. Even if a future build configuration accidentally
    // bakes a non-empty DEV_TOKEN_KEY into a non-.dev APK, this guard keeps
    // the backdoor closed at the client level. Server-side guards (env=dev,
    // .test email regex, key match) provide the second and third lines of
    // defense.
    if (!BuildConfig.APPLICATION_ID.endsWith(".dev")) {
      _state.value = LoginUiState.Error("Dev login is unavailable in this build.")
      return
    }
    val key = BuildConfig.DEV_TOKEN_KEY
    if (key.isBlank()) {
      _state.value = LoginUiState.Error("DEV_TOKEN_KEY missing — debug builds only")
      return
    }
    viewModelScope.launch {
      _state.value = LoginUiState.Loading
      runCatching { api.devToken(key, DevTokenRequest(email = "dev@pantrie.test")) }
        .onSuccess { resp ->
          tokenStore.saveRefreshToken(resp.refreshToken)
          tokenStore.saveUserId(resp.userId)
          tokenStore.setAccess(resp.accessToken, resp.expiresAt)
          _state.value = LoginUiState.LoggedIn
        }
        .onFailure { e ->
          _state.value = LoginUiState.Error(e.message ?: "Dev login failed")
        }
    }
  }

  fun clearError() { _state.value = LoginUiState.Idle }
}

@Composable
fun LoginScreen(
  onLoggedIn: () -> Unit,
  vm: LoginViewModel = hiltViewModel(),
) {
  val state by vm.state.collectAsState()
  val context = LocalContext.current
  val activity = context as? Activity

  LaunchedEffect(state) {
    if (state is LoginUiState.LoggedIn) onLoggedIn()
  }

  Surface(modifier = Modifier.fillMaxSize(), color = Paper) {
    Column(
      modifier = Modifier.fillMaxSize().padding(24.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(app.pantrie.Brand.APP_NAME, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Normal, color = Ink)
      Spacer(Modifier.height(8.dp))
      Text(
        "see it. save it. savor it.",
        style = MaterialTheme.typography.bodyLarge, color = InkSoft,
      )
      Spacer(Modifier.height(48.dp))

      when (val s = state) {
        LoginUiState.Loading -> CircularProgressIndicator(color = BrassBright, strokeWidth = 2.dp)
        is LoginUiState.Error -> {
          Text(
            s.message,
            style = MaterialTheme.typography.bodyMedium, color = Terracotta,
          )
          Spacer(Modifier.height(16.dp))
          GoogleSignInButton(
            onClick = {
              vm.clearError()
              activity?.let { vm.signInWithGoogle(it) }
            },
            enabled = activity != null,
          )
          // Debug-only fallback in case Google sign-in errors mid-test on a debug build.
          if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { vm.clearError(); vm.devLogin() }) {
              Text("Dev login (debug only)", color = InkMuted, fontSize = 12.sp)
            }
          }
        }
        else -> {
          GoogleSignInButton(
            onClick = { activity?.let { vm.signInWithGoogle(it) } },
            enabled = activity != null,
          )
          Spacer(Modifier.height(12.dp))
          Text(
            "We use Google for sign-in so you don't have a password to remember. We never see it.",
            style = MaterialTheme.typography.labelSmall, color = InkMuted,
          )
          // Debug-only fallback. Stripped from release.
          if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = vm::devLogin) {
              Text("Dev login (debug only)", color = InkMuted, fontSize = 11.sp)
            }
          }
        }
      }
    }
  }
}

/** Google-branded sign-in button. White surface, dark text, official "G" tile. */
@Composable
private fun GoogleSignInButton(onClick: () -> Unit, enabled: Boolean) {
  Surface(
    onClick = onClick,
    enabled = enabled,
    shape = RoundedCornerShape(28.dp),
    color = Color.White,
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDADCE0)),
    modifier = Modifier.fillMaxWidth().height(56.dp),
  ) {
    Row(
      Modifier.fillMaxSize().padding(horizontal = 20.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      // Google "G" mark — quartered colored squares around white center, the
      // official simplified version that's safe to use without a license file.
      Box(
        Modifier.size(20.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          "G",
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF4285F4),
        )
      }
      Spacer(Modifier.width(14.dp))
      Text(
        "Continue with Google",
        color = Color(0xFF3C4043),
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
      )
    }
  }
}
