package app.pantrie.feature.submit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.feature.beta.Analytics
import app.pantrie.network.PantrieApi
import app.pantrie.network.dto.PhotoUploadRequest
import app.pantrie.network.dto.SubmitIngredient
import app.pantrie.network.dto.SubmitRecipeRequest
import app.pantrie.network.dto.SubmitStep
import app.pantrie.ui.theme.*
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

data class DraftIngredient(val name: String = "", val quantity: String = "", val unit: String = "")
data class DraftStep(val text: String = "")

data class SubmitUiState(
  val title: String = "",
  val cuisine: String = "",
  val description: String = "",
  val prepMinutes: String = "",
  val cookMinutes: String = "",
  val servings: String = "",
  val ingredients: List<DraftIngredient> = listOf(DraftIngredient(), DraftIngredient()),
  val steps: List<DraftStep> = listOf(DraftStep()),
  val photoUrl: String? = null,
  val uploading: Boolean = false,
  val submitting: Boolean = false,
  val error: String? = null,
  val submittedId: String? = null,
  val dupOfTitle: String? = null,
)

@HiltViewModel
class SubmitRecipeViewModel @Inject constructor(
  private val api: PantrieApi,
  private val analytics: Analytics,
) : ViewModel() {
  private val _state = MutableStateFlow(SubmitUiState())
  val state = _state.asStateFlow()

  fun set(field: String, value: String) {
    _state.value = when (field) {
      "title" -> _state.value.copy(title = value)
      "cuisine" -> _state.value.copy(cuisine = value)
      "description" -> _state.value.copy(description = value)
      "prep" -> _state.value.copy(prepMinutes = value.filter { it.isDigit() })
      "cook" -> _state.value.copy(cookMinutes = value.filter { it.isDigit() })
      "servings" -> _state.value.copy(servings = value.filter { it.isDigit() })
      else -> _state.value
    }
  }

  fun setIngredient(i: Int, field: String, v: String) {
    val list = _state.value.ingredients.toMutableList()
    val cur = list[i]
    list[i] = when (field) {
      "name" -> cur.copy(name = v)
      "qty" -> cur.copy(quantity = v)
      "unit" -> cur.copy(unit = v)
      else -> cur
    }
    _state.value = _state.value.copy(ingredients = list)
  }

  fun addIngredient() {
    if (_state.value.ingredients.size >= 40) return
    _state.value = _state.value.copy(ingredients = _state.value.ingredients + DraftIngredient())
  }

  fun removeIngredient(i: Int) {
    if (_state.value.ingredients.size <= 2) return
    _state.value = _state.value.copy(ingredients = _state.value.ingredients.toMutableList().apply { removeAt(i) })
  }

  fun setStep(i: Int, v: String) {
    val list = _state.value.steps.toMutableList()
    list[i] = DraftStep(v)
    _state.value = _state.value.copy(steps = list)
  }

  fun addStep() {
    if (_state.value.steps.size >= 30) return
    _state.value = _state.value.copy(steps = _state.value.steps + DraftStep())
  }

  fun removeStep(i: Int) {
    if (_state.value.steps.size <= 1) return
    _state.value = _state.value.copy(steps = _state.value.steps.toMutableList().apply { removeAt(i) })
  }

  fun uploadPhoto(ctx: Context, uri: Uri) {
    viewModelScope.launch {
      _state.value = _state.value.copy(uploading = true, error = null)
      val dataUrl = runCatching { withContext(Dispatchers.IO) { uriToJpegDataUrl(ctx, uri) } }.getOrNull()
      if (dataUrl == null) {
        _state.value = _state.value.copy(uploading = false, error = "Couldn't read image")
        return@launch
      }
      runCatching { api.uploadSubmissionPhoto(PhotoUploadRequest(image = dataUrl)) }
        .onSuccess {
          _state.value = _state.value.copy(uploading = false, photoUrl = it.url)
          analytics.track("submission_photo_uploaded")
        }
        .onFailure { e ->
          _state.value = _state.value.copy(uploading = false, error = "Upload failed: ${e.message?.take(80)}")
        }
    }
  }

  fun clearPhoto() { _state.value = _state.value.copy(photoUrl = null) }

  fun submit() {
    val s = _state.value
    if (s.submitting) return
    if (s.title.trim().length < 3) { _state.value = s.copy(error = "Title needs at least 3 characters"); return }
    if (s.photoUrl.isNullOrBlank()) { _state.value = s.copy(error = "Photo is required"); return }
    val ings = s.ingredients.mapNotNull { d ->
      val n = d.name.trim()
      if (n.isEmpty()) null else SubmitIngredient(
        name = n,
        quantity = d.quantity.trim().toDoubleOrNull(),
        unit = d.unit.trim().ifBlank { null },
        aisle = null,
      )
    }
    if (ings.size < 2) { _state.value = s.copy(error = "Need at least 2 ingredients"); return }
    val steps = s.steps.mapNotNull { d -> d.text.trim().takeIf { it.length >= 4 }?.let { SubmitStep(it) } }
    if (steps.isEmpty()) { _state.value = s.copy(error = "Need at least 1 cooking step"); return }

    _state.value = s.copy(submitting = true, error = null)
    viewModelScope.launch {
      runCatching {
        api.submitRecipe(SubmitRecipeRequest(
          title = s.title.trim(),
          cuisine = s.cuisine.trim().ifBlank { null },
          description = s.description.trim().ifBlank { null },
          prepMinutes = s.prepMinutes.toIntOrNull(),
          cookMinutes = s.cookMinutes.toIntOrNull(),
          servings = s.servings.toIntOrNull(),
          ingredients = ings,
          steps = steps,
          imageUrl = s.photoUrl,
        ))
      }.onSuccess { resp ->
        analytics.track("recipe_submitted", mapOf("status" to resp.status))
        _state.value = _state.value.copy(
          submitting = false,
          submittedId = resp.id,
          dupOfTitle = resp.dupOf?.title,
        )
      }.onFailure { e ->
        _state.value = _state.value.copy(submitting = false, error = e.message?.take(120) ?: "Submit failed")
      }
    }
  }
}

private fun uriToJpegDataUrl(ctx: Context, uri: Uri): String {
  // Read EXIF orientation FIRST from a dedicated input stream so we can rotate
  // the decoded bitmap correctly. Bitmap.compress(JPEG) does not carry EXIF,
  // so re-encoding already strips GPS/camera metadata — we just need to ensure
  // the pixels are upright before discarding the orientation tag.
  val orientation = runCatching {
    ctx.contentResolver.openInputStream(uri)?.use { input ->
      androidx.exifinterface.media.ExifInterface(input).getAttributeInt(
        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
        androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL,
      )
    } ?: androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
  }.getOrDefault(androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)

  ctx.contentResolver.openInputStream(uri).use { input ->
    val decoded = BitmapFactory.decodeStream(input) ?: throw IllegalStateException("decode failed")
    val upright = applyExifOrientation(decoded, orientation)
    // Downscale to max 1600px on the long edge to keep upload under ~400KB
    val maxDim = 1600
    val scale = minOf(1f, maxDim.toFloat() / maxOf(upright.width, upright.height))
    val scaled = if (scale < 1f)
      Bitmap.createScaledBitmap(upright, (upright.width * scale).toInt(), (upright.height * scale).toInt(), true)
    else upright
    val bos = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 85, bos)
    val bytes = bos.toByteArray()
    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
    return "data:image/jpeg;base64,$b64"
  }
}

private fun applyExifOrientation(bmp: Bitmap, orientation: Int): Bitmap {
  val matrix = android.graphics.Matrix()
  when (orientation) {
    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
    androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
    androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
    androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
    androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
    else -> return bmp
  }
  return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmitRecipeScreen(
  onBack: () -> Unit,
  vm: SubmitRecipeViewModel = hiltViewModel(),
) {
  val s by vm.state.collectAsState()
  val ctx = LocalContext.current
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) vm.uploadPhoto(ctx, uri)
  }

  if (s.submittedId != null) {
    SubmittedScreen(dupTitle = s.dupOfTitle, onBack = onBack)
    return
  }

  Scaffold(
    containerColor = Cream,
    topBar = {
      TopAppBar(
        title = { Text("Submit a recipe", fontWeight = FontWeight.Normal) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream),
      )
    },
  ) { padding ->
    LazyColumn(
      Modifier.padding(padding).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Text(
          "Photo is required. It'll be reviewed before going live in the catalog.",
          style = MaterialTheme.typography.bodyMedium, color = InkMuted,
        )
      }

      item {
        PhotoCard(
          photoUrl = s.photoUrl, uploading = s.uploading,
          onPick = { picker.launch(androidx.activity.result.PickVisualMediaRequest(
            ActivityResultContracts.PickVisualMedia.ImageOnly
          )) },
          onClear = vm::clearPhoto,
        )
      }

      item {
        OutlinedTextField(
          value = s.title, onValueChange = { vm.set("title", it.take(100)) },
          label = { Text("Title *") }, singleLine = true,
          modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp),
        )
      }
      item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = s.cuisine, onValueChange = { vm.set("cuisine", it.take(40)) },
            label = { Text("Cuisine") }, singleLine = true,
            modifier = Modifier.weight(1f), shape = RoundedCornerShape(4.dp),
          )
          OutlinedTextField(
            value = s.servings, onValueChange = { vm.set("servings", it) },
            label = { Text("Servings") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(110.dp), shape = RoundedCornerShape(4.dp),
          )
        }
      }
      item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = s.prepMinutes, onValueChange = { vm.set("prep", it) },
            label = { Text("Prep min") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f), shape = RoundedCornerShape(4.dp),
          )
          OutlinedTextField(
            value = s.cookMinutes, onValueChange = { vm.set("cook", it) },
            label = { Text("Cook min") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f), shape = RoundedCornerShape(4.dp),
          )
        }
      }
      item {
        OutlinedTextField(
          value = s.description, onValueChange = { vm.set("description", it.take(400)) },
          label = { Text("Short description") },
          modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
          shape = RoundedCornerShape(4.dp),
        )
      }

      item {
        SectionHeader("Ingredients", "Tap + to add more (2-40 required)")
      }
      itemsIndexed(s.ingredients) { i, ing ->
        IngredientRow(
          ing = ing,
          onChange = { field, v -> vm.setIngredient(i, field, v) },
          onRemove = if (s.ingredients.size > 2) ({ vm.removeIngredient(i) }) else null,
        )
      }
      item {
        OutlinedButton(onClick = vm::addIngredient, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp)) {
          Text("+ Add ingredient", color = Ink)
        }
      }

      item { SectionHeader("Steps", "At least one") }
      itemsIndexed(s.steps) { i, st ->
        StepRow(
          index = i + 1, text = st.text,
          onChange = { vm.setStep(i, it) },
          onRemove = if (s.steps.size > 1) ({ vm.removeStep(i) }) else null,
        )
      }
      item {
        OutlinedButton(onClick = vm::addStep, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp)) {
          Text("+ Add step", color = Ink)
        }
      }

      s.error?.let {
        item {
          Text(it, color = Terracotta, style = MaterialTheme.typography.bodyMedium)
        }
      }

      item {
        Button(
          onClick = vm::submit,
          enabled = !s.submitting && !s.uploading && s.photoUrl != null,
          modifier = Modifier.fillMaxWidth().height(52.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Terracotta),
          shape = RoundedCornerShape(4.dp),
        ) {
          if (s.submitting) CircularProgressIndicator(Modifier.size(18.dp), color = Paper, strokeWidth = 2.dp)
          else Text("Submit for review", color = Paper, fontWeight = FontWeight.SemiBold)
        }
      }
      item { Spacer(Modifier.height(24.dp)) }
    }
  }
}

@Composable
private fun PhotoCard(photoUrl: String?, uploading: Boolean, onPick: () -> Unit, onClear: () -> Unit) {
  Surface(
    onClick = { if (photoUrl == null && !uploading) onPick() },
    shape = RoundedCornerShape(12.dp),
    color = Paper,
    modifier = Modifier.fillMaxWidth().height(200.dp),
  ) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      when {
        uploading -> CircularProgressIndicator(color = Ink, strokeWidth = 2.dp)
        photoUrl != null -> {
          AsyncImage(
            model = photoUrl, contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
          )
          Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.TopEnd) {
            IconButton(
              onClick = onClear,
              colors = IconButtonDefaults.iconButtonColors(containerColor = Ink.copy(alpha = 0.7f)),
            ) { Icon(Icons.Outlined.Close, "remove photo", tint = Paper) }
          }
        }
        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(Icons.Outlined.AddPhotoAlternate, null, tint = InkMuted, modifier = Modifier.size(32.dp))
          Spacer(Modifier.height(8.dp))
          Text("Tap to add photo", color = Ink, fontWeight = FontWeight.Medium)
          Text("Required", color = InkMuted, style = MaterialTheme.typography.labelSmall)
        }
      }
    }
  }
}

@Composable
private fun SectionHeader(title: String, hint: String) {
  Column {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Ink)
    Text(hint, style = MaterialTheme.typography.labelSmall, color = InkMuted)
  }
}

@Composable
private fun IngredientRow(ing: DraftIngredient, onChange: (String, String) -> Unit, onRemove: (() -> Unit)?) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    OutlinedTextField(
      value = ing.quantity, onValueChange = { onChange("qty", it) },
      label = { Text("Qty") }, singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      modifier = Modifier.width(72.dp), shape = RoundedCornerShape(4.dp),
    )
    Spacer(Modifier.width(6.dp))
    OutlinedTextField(
      value = ing.unit, onValueChange = { onChange("unit", it.take(10)) },
      label = { Text("Unit") }, singleLine = true,
      modifier = Modifier.width(80.dp), shape = RoundedCornerShape(4.dp),
    )
    Spacer(Modifier.width(6.dp))
    OutlinedTextField(
      value = ing.name, onValueChange = { onChange("name", it.take(80)) },
      label = { Text("Ingredient") }, singleLine = true,
      modifier = Modifier.weight(1f), shape = RoundedCornerShape(4.dp),
    )
    if (onRemove != null) {
      IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, "remove", tint = InkFaint) }
    }
  }
}

@Composable
private fun StepRow(index: Int, text: String, onChange: (String) -> Unit, onRemove: (() -> Unit)?) {
  Row(verticalAlignment = Alignment.Top) {
    Text(
      "$index.",
      modifier = Modifier.padding(top = 18.dp, end = 8.dp),
      color = InkMuted, fontWeight = FontWeight.Medium,
    )
    OutlinedTextField(
      value = text, onValueChange = { onChange(it.take(500)) },
      label = { Text("Step") },
      modifier = Modifier.weight(1f).heightIn(min = 72.dp),
      shape = RoundedCornerShape(4.dp),
    )
    if (onRemove != null) {
      IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, "remove", tint = InkFaint) }
    }
  }
}

@Composable
private fun SubmittedScreen(dupTitle: String?, onBack: () -> Unit) {
  Scaffold(containerColor = Cream) { padding ->
    Column(
      Modifier.padding(padding).fillMaxSize().padding(32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Text(
        if (dupTitle == null) "Submitted!" else "Submitted — possible duplicate",
        style = MaterialTheme.typography.displaySmall, color = Ink,
      )
      Spacer(Modifier.height(12.dp))
      Text(
        if (dupTitle == null)
          "Your recipe is in the review queue. You'll see it appear in your Saved once approved."
        else
          "Looks similar to an existing recipe: \"$dupTitle\". The admin will decide. You can track status in 'My submissions'.",
        style = MaterialTheme.typography.bodyLarge, color = InkSoft,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
      )
      Spacer(Modifier.height(24.dp))
      Button(
        onClick = onBack,
        colors = ButtonDefaults.buttonColors(containerColor = Ink),
        shape = RoundedCornerShape(4.dp),
      ) { Text("Done", color = Paper) }
    }
  }
}

