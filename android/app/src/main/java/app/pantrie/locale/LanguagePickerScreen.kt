@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pantrie.locale

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.R
import app.pantrie.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LanguagePickerViewModel @Inject constructor(
  private val locale: LocaleManager,
) : ViewModel() {
  private val _selected = MutableStateFlow(AppLocale.ENGLISH)
  val selected = _selected.asStateFlow()

  fun pick(l: AppLocale) { _selected.value = l }

  fun commit(onDone: () -> Unit) {
    locale.set(_selected.value)
    onDone()
  }
}

/**
 * First-run language picker. Shown before login on first install (gated by
 * LocaleManager.hasPicked()). Each row labels the language in its OWN tongue
 * so a user who can't read the current locale can still recognize their own
 * language and pick it.
 *
 * After Continue the activity is recreated by AppCompatDelegate to apply
 * the new locale, so MainActivity's nav re-evaluates `hasPicked()` and
 * proceeds to login.
 */
@Composable
fun LanguagePickerScreen(
  onPicked: () -> Unit,
  vm: LanguagePickerViewModel = hiltViewModel(),
) {
  val selected by vm.selected.collectAsState()
  Surface(modifier = Modifier.fillMaxSize(), color = Paper) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 28.dp, vertical = 32.dp),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Column {
        Text(
          stringResource(R.string.app_name),
          style = MaterialTheme.typography.displaySmall,
          color = Ink,
          fontWeight = FontWeight.Normal,
        )
        Spacer(Modifier.height(28.dp))
        Text(
          stringResource(R.string.lang_picker_title),
          style = MaterialTheme.typography.headlineSmall,
          color = Ink,
          fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
          stringResource(R.string.lang_picker_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = InkSoft,
        )
        Spacer(Modifier.height(24.dp))
        // Each row shows BOTH the native name (primary) and the English name
        // (secondary subtext) so the difference is unambiguous. Just rendering
        // "Español / Português / Bahasa Indonesia" looked English-ish to users
        // because most of those happen to use the Latin alphabet — an English-
        // labelled subtitle removes the doubt.
        AppLocale.entries.forEach { lang ->
          val isSelected = selected == lang
          val (nativeLabel, englishLabel, flag) = when (lang) {
            AppLocale.ENGLISH    -> Triple(stringResource(R.string.lang_name_english),    "English",                    "🇺🇸")
            AppLocale.SPANISH    -> Triple(stringResource(R.string.lang_name_spanish),    "Spanish",                    "🇪🇸")
            AppLocale.PORTUGUESE -> Triple(stringResource(R.string.lang_name_portuguese), "Portuguese (Brazil)",        "🇧🇷")
            AppLocale.HINDI      -> Triple(stringResource(R.string.lang_name_hindi),      "Hindi",                      "🇮🇳")
            AppLocale.INDONESIAN -> Triple(stringResource(R.string.lang_name_indonesian), "Indonesian",                 "🇮🇩")
          }
          LanguageRow(
            nativeLabel = nativeLabel,
            englishLabel = englishLabel,
            flag = flag,
            selected = isSelected,
            onClick = { vm.pick(lang) },
          )
          Spacer(Modifier.height(10.dp))
        }
      }
      Button(
        onClick = { vm.commit(onPicked) },
        colors = ButtonDefaults.buttonColors(
          containerColor = BrassBright,
          contentColor = Ink,
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().height(56.dp),
      ) {
        Text(
          stringResource(R.string.lang_picker_continue),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          letterSpacing = 1.5.sp,
        )
      }
    }
  }
}

@Composable
private fun LanguageRow(
  nativeLabel: String,
  englishLabel: String,
  flag: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Surface(
    color = if (selected) BrassBright.copy(alpha = 0.18f) else Paper2,
    shape = RoundedCornerShape(10.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(
        width = if (selected) 2.dp else 1.dp,
        color = if (selected) BrassBright else InkFaint.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
      )
      .clickable(onClick = onClick),
  ) {
    Row(
      Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(flag, fontSize = 24.sp)
      Spacer(Modifier.width(14.dp))
      Column(Modifier.weight(1f)) {
        Text(
          nativeLabel,
          style = MaterialTheme.typography.titleMedium,
          color = Ink,
          fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(
          englishLabel,
          style = MaterialTheme.typography.labelSmall,
          color = InkSoft,
          letterSpacing = 1.0.sp,
        )
      }
      if (selected) {
        Text(
          "✓",
          style = MaterialTheme.typography.titleMedium,
          color = BrassBright,
          fontWeight = FontWeight.Bold,
        )
      }
    }
  }
}
