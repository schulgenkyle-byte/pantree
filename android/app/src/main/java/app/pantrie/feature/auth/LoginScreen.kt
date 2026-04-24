package app.pantrie.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.BuildConfig
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
) : ViewModel() {

  private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
  val state = _state.asStateFlow()

  init {
    // If we already have a refresh token (returning user) skip the login screen.
    if (tokenStore.getRefreshToken() != null) {
      _state.value = LoginUiState.LoggedIn
    }
  }

  fun devLogin() {
    val key = BuildConfig.DEV_TOKEN_KEY
    if (key.isBlank()) {
      _state.value = LoginUiState.Error("PANTRIE_DEV_TOKEN_KEY missing in gradle.properties")
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
          _state.value = LoginUiState.Error(e.message ?: "Login failed")
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

  LaunchedEffect(state) {
    if (state is LoginUiState.LoggedIn) onLoggedIn()
  }

  Surface(modifier = Modifier.fillMaxSize(), color = Cream) {
    Column(
      modifier = Modifier.fillMaxSize().padding(24.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text("pan-tree", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Normal, color = Ink)
      Spacer(Modifier.height(8.dp))
      Text(
        "see it. save it. savor it.",
        style = MaterialTheme.typography.bodyLarge, color = InkSoft,
      )
      Spacer(Modifier.height(48.dp))

      when (val s = state) {
        LoginUiState.Loading -> CircularProgressIndicator(color = Ink, strokeWidth = 2.dp)
        is LoginUiState.Error -> {
          Text(
            "Error: ${s.message}",
            style = MaterialTheme.typography.bodyMedium, color = Terracotta,
          )
          Spacer(Modifier.height(16.dp))
          Button(
            onClick = { vm.clearError(); vm.devLogin() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink),
            shape = RoundedCornerShape(4.dp),
          ) { Text("Try again", color = Paper, fontWeight = FontWeight.SemiBold) }
        }
        else -> {
          Button(
            onClick = vm::devLogin,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink),
            shape = RoundedCornerShape(4.dp),
          ) { Text("Dev login", color = Paper, fontWeight = FontWeight.SemiBold) }
          Spacer(Modifier.height(12.dp))
          Text(
            "Google sign-in coming soon. This button uses a dev-only backdoor that only works against the staging server.",
            style = MaterialTheme.typography.labelSmall, color = InkMuted,
          )
        }
      }
    }
  }
}
