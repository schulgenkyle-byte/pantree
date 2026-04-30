@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pantrie.feature.submit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.Brand
import app.pantrie.billing.EntitlementRepository
import app.pantrie.billing.ProUpgradeCard
import app.pantrie.feature.beta.Analytics
import app.pantrie.network.PantrieApi
import app.pantrie.network.dto.ExtractRecipeRequest
import app.pantrie.network.dto.ExtractedIngredient
import app.pantrie.network.dto.StructuredIngredient
import app.pantrie.network.dto.StructuredSubmitRequest
import app.pantrie.ui.theme.*
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject

// ----- Canonical lists. MUST stay in lockstep with backend/src/submissions.js
//       (CANONICAL_INGREDIENT_SLUGS / CANONICAL_UNITS / CANONICAL_CUISINES) and with
//       android/.../ui/IngredientImage.kt INGREDIENT_RULES values.

private val INGREDIENT_SLUGS: List<String> = listOf(
  "sweet_potato","bell_pepper","chili","tomato","onion","garlic","potato","carrot",
  "cucumber","broccoli","leafy_greens","eggplant","corn","avocado","mushroom","lemon",
  "lime","orange","apple","banana","grape","strawberry","berries","peach","pear",
  "cherry","pineapple","watermelon","melon","mango","kiwi","coconut","olive","pea",
  "ginger","herbs",
  "chicken","turkey","beef","pork","lamb","sausage","fish","shrimp","lobster",
  "shellfish","octopus","egg","tofu",
  "milk","cheese","butter","yogurt",
  "bagel","croissant","pancake","tortilla","taco","burrito","pizza","dumpling",
  "sushi","oatmeal","pasta","rice","bread","flour",
  "beans","nuts","seeds",
  "honey","oil","sauce","condiment","salt","vanilla","chocolate",
  "cookie","cake","pie","ice_cream",
  "water","coffee","tea","wine","beer","cocktail_generic","juice",
  "frozen","soup","salad","popcorn","candy",
)

private val UNIT_CHOICES: List<String> = listOf(
  "cup","tbsp","tsp","oz","g","kg","ml","l","whole","slice","clove","pinch","dash"
)

private val CUISINE_CHOICES: List<String> = listOf(
  "italian","mexican","japanese","chinese","indian","thai","french","american",
  "mediterranean","korean","vietnamese","middle-eastern",
)

private val CONTENT_TYPE_CHOICES: List<Pair<String, String>> = listOf(
  "food" to "Food",
  "cocktail" to "Cocktail",
  "mocktail" to "Mocktail",
)

/** Slug like "sweet_potato" → "Sweet potato" for human display. */
private fun prettifySlug(slug: String): String =
  slug.replace('_', ' ').replaceFirstChar { it.uppercase() }

// ---------------------------------------------------------------------------------------------
// State machine
// ---------------------------------------------------------------------------------------------
sealed interface PhotoToRecipePhase {
  object Pick : PhotoToRecipePhase                       // pick a photo
  object Extracting : PhotoToRecipePhase                 // calling Vision
  data class Form(val photoUri: Uri?) : PhotoToRecipePhase
  object Submitted : PhotoToRecipePhase
}

data class FormIngredient(
  val canonical: String = "",     // empty until user picks
  val quantity: String = "",      // string for editable numeric field
  val unit: String = "",
)

data class PhotoToRecipeForm(
  val title: String = "",
  val cuisine: String = "",
  val contentType: String = "food",
  val servings: Int? = null,
  val timeMinutes: Int? = null,
  val ingredients: List<FormIngredient> = listOf(FormIngredient(), FormIngredient()),
  val steps: List<String> = listOf(""),
)

data class PhotoToRecipeUi(
  val phase: PhotoToRecipePhase = PhotoToRecipePhase.Pick,
  val photoUri: Uri? = null,
  val form: PhotoToRecipeForm = PhotoToRecipeForm(),
  val submitting: Boolean = false,
  val error: String? = null,
  val notExtractable: Boolean = false,
  val proGate: Boolean = false,         // server returned 402
  val submittedId: String? = null,
)

// ---------------------------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------------------------
@HiltViewModel
class PhotoToRecipeViewModel @Inject constructor(
  private val api: PantrieApi,
  val entitlement: EntitlementRepository,
  private val analytics: Analytics,
) : ViewModel() {
  private val _state = MutableStateFlow(PhotoToRecipeUi())
  val state = _state.asStateFlow()

  init {
    // Refresh entitlement on entry — covers the case where the user just bought Pro
    // from another tab and the cached flag hasn't synced yet.
    viewModelScope.launch { runCatching { entitlement.refresh() } }
  }

  fun resetToPicker() {
    _state.value = PhotoToRecipeUi()
  }

  fun onPhotoPicked(ctx: Context, uri: Uri) {
    _state.value = _state.value.copy(
      photoUri = uri,
      phase = PhotoToRecipePhase.Extracting,
      notExtractable = false,
      proGate = false,
      error = null,
    )
    analytics.track("photo_to_recipe_extract_started")
    viewModelScope.launch {
      val dataUrl = runCatching {
        withContext(Dispatchers.IO) { uriToJpegDataUrl(ctx, uri) }
      }.getOrNull()
      if (dataUrl == null) {
        _state.value = _state.value.copy(
          phase = PhotoToRecipePhase.Pick,
          error = "Couldn't read that photo — try another.",
        )
        return@launch
      }
      val resp = runCatching { api.extractRecipeFromPhoto(ExtractRecipeRequest(photoBase64 = dataUrl)) }
      resp.onFailure { e ->
        _state.value = _state.value.copy(
          phase = PhotoToRecipePhase.Pick,
          error = "Extract failed (${e.message?.take(80) ?: "network"}). Try again.",
        )
      }.onSuccess { r ->
        when (r.code()) {
          200 -> {
            val body = r.body()
            if (body == null || !body.extractable) {
              // 200 with extractable:false is technically possible if the server changes — handle it.
              _state.value = _state.value.copy(
                phase = PhotoToRecipePhase.Form(uri),
                notExtractable = true,
                form = PhotoToRecipeForm(),
              )
              analytics.track("photo_to_recipe_not_extractable")
            } else {
              _state.value = _state.value.copy(
                phase = PhotoToRecipePhase.Form(uri),
                form = body.toForm(),
                notExtractable = false,
              )
              analytics.track("photo_to_recipe_extracted", mapOf(
                "ingredients" to body.ingredients.size,
                "steps" to body.steps.size,
              ))
            }
          }
          422 -> {
            // Couldn't extract a recipe — let user fill out form from scratch instead.
            _state.value = _state.value.copy(
              phase = PhotoToRecipePhase.Form(uri),
              notExtractable = true,
              form = PhotoToRecipeForm(),
            )
            analytics.track("photo_to_recipe_not_extractable")
          }
          402 -> {
            _state.value = _state.value.copy(
              phase = PhotoToRecipePhase.Pick,
              proGate = true,
            )
          }
          429 -> {
            _state.value = _state.value.copy(
              phase = PhotoToRecipePhase.Pick,
              error = "Daily extract limit reached. Try again tomorrow.",
            )
          }
          else -> {
            _state.value = _state.value.copy(
              phase = PhotoToRecipePhase.Pick,
              error = "Extract failed (${r.code()}). Try again.",
            )
          }
        }
      }
    }
  }

  fun setTitle(v: String) = mutateForm { it.copy(title = v.take(120)) }
  fun setCuisine(v: String) = mutateForm { it.copy(cuisine = v) }
  fun setContentType(v: String) = mutateForm { it.copy(contentType = v) }
  fun setServings(delta: Int) = mutateForm { it.copy(servings = ((it.servings ?: 0) + delta).coerceIn(0, 20).takeIf { n -> n > 0 }) }
  fun setTimeMinutes(delta: Int) = mutateForm { it.copy(timeMinutes = ((it.timeMinutes ?: 0) + delta * 5).coerceIn(0, 600).takeIf { n -> n > 0 }) }

  fun setIngredient(i: Int, ing: FormIngredient) = mutateForm { f ->
    val list = f.ingredients.toMutableList()
    if (i in list.indices) list[i] = ing
    f.copy(ingredients = list)
  }
  fun addIngredient() = mutateForm { f ->
    if (f.ingredients.size >= 30) f else f.copy(ingredients = f.ingredients + FormIngredient())
  }
  fun removeIngredient(i: Int) = mutateForm { f ->
    if (f.ingredients.size <= 1) f
    else f.copy(ingredients = f.ingredients.toMutableList().apply { removeAt(i) })
  }

  fun setStep(i: Int, text: String) = mutateForm { f ->
    val list = f.steps.toMutableList()
    if (i in list.indices) list[i] = text.take(500)
    f.copy(steps = list)
  }
  fun addStep() = mutateForm { f ->
    if (f.steps.size >= 30) f else f.copy(steps = f.steps + "")
  }
  fun removeStep(i: Int) = mutateForm { f ->
    if (f.steps.size <= 1) f else f.copy(steps = f.steps.toMutableList().apply { removeAt(i) })
  }
  fun moveStep(i: Int, delta: Int) = mutateForm { f ->
    val target = i + delta
    if (i !in f.steps.indices || target !in f.steps.indices) f
    else f.copy(steps = f.steps.toMutableList().apply { val tmp = this[i]; this[i] = this[target]; this[target] = tmp })
  }

  private fun mutateForm(block: (PhotoToRecipeForm) -> PhotoToRecipeForm) {
    _state.value = _state.value.copy(form = block(_state.value.form))
  }

  fun submit() {
    val s = _state.value
    if (s.submitting) return
    val f = s.form
    val title = f.title.trim()
    if (title.length < 5) { _state.value = s.copy(error = "Title needs at least 5 characters"); return }
    val ings = f.ingredients
      .filter { it.canonical.isNotBlank() && INGREDIENT_SLUGS.contains(it.canonical) }
      .map { row ->
        StructuredIngredient(
          canonicalName = row.canonical,
          quantity = row.quantity.trim().toDoubleOrNull(),
          unit = row.unit.takeIf { it.isNotBlank() && UNIT_CHOICES.contains(it) },
        )
      }
    if (ings.size < 2) { _state.value = s.copy(error = "Pick at least 2 ingredients"); return }
    val steps = f.steps.map { it.trim() }.filter { it.length >= 4 }
    if (steps.isEmpty()) { _state.value = s.copy(error = "Add at least 1 step"); return }

    _state.value = s.copy(submitting = true, error = null)
    viewModelScope.launch {
      runCatching {
        api.submitStructuredRecipe(StructuredSubmitRequest(
          title = title,
          cuisine = f.cuisine.takeIf { it.isNotBlank() && CUISINE_CHOICES.contains(it) },
          contentType = f.contentType,
          servings = f.servings,
          timeMinutes = f.timeMinutes,
          ingredients = ings,
          steps = steps,
          imageUrl = null,
        ))
      }.onSuccess { resp ->
        analytics.track("photo_to_recipe_submitted", mapOf("status" to resp.status))
        _state.value = _state.value.copy(
          submitting = false,
          submittedId = resp.id,
          phase = PhotoToRecipePhase.Submitted,
        )
      }.onFailure { e ->
        val http = e as? HttpException
        val msg = when (http?.code()) {
          402 -> { _state.value = _state.value.copy(submitting = false, proGate = true); return@launch }
          400 -> "That doesn't look right — server rejected the recipe. Check your fields."
          else -> e.message?.take(120) ?: "Submit failed"
        }
        _state.value = _state.value.copy(submitting = false, error = msg)
      }
    }
  }
}

private fun app.pantrie.network.dto.ExtractRecipeResponse.toForm(): PhotoToRecipeForm {
  // Pre-fill the form with whatever Claude managed to extract. Always pad ingredients
  // to >= 2 rows so the user has visible empty rows to add to.
  val ings = ingredients.map { e: ExtractedIngredient ->
    FormIngredient(
      canonical = e.canonicalName,
      quantity = e.quantity?.let { if (it == it.toInt().toDouble()) it.toInt().toString() else it.toString() } ?: "",
      unit = e.unit ?: "",
    )
  }.let { if (it.size >= 2) it else it + List(2 - it.size) { FormIngredient() } }
  val stepList = if (steps.isEmpty()) listOf("") else steps
  return PhotoToRecipeForm(
    title = title,
    cuisine = cuisine ?: "",
    contentType = contentType,
    servings = servings,
    timeMinutes = timeMinutes,
    ingredients = ings,
    steps = stepList,
  )
}

// ---------------------------------------------------------------------------------------------
// Photo encoding helper — matches SubmitRecipeScreen's EXIF/upright pattern.
// ---------------------------------------------------------------------------------------------
private fun uriToJpegDataUrl(ctx: Context, uri: Uri): String {
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
    val upright = applyExif(decoded, orientation)
    val maxDim = 1600
    val scale = minOf(1f, maxDim.toFloat() / maxOf(upright.width, upright.height))
    val scaled = if (scale < 1f)
      Bitmap.createScaledBitmap(upright, (upright.width * scale).toInt(), (upright.height * scale).toInt(), true)
    else upright
    val bos = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 85, bos)
    val b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    return "data:image/jpeg;base64,$b64"
  }
}

private fun applyExif(bmp: Bitmap, orientation: Int): Bitmap {
  val matrix = android.graphics.Matrix()
  when (orientation) {
    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
    else -> return bmp
  }
  return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
}

private fun createCacheUri(ctx: Context): Uri {
  val dir = File(ctx.cacheDir, "submit").apply { mkdirs() }
  val file = File(dir, "${UUID.randomUUID()}.jpg")
  return FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
}

// ---------------------------------------------------------------------------------------------
// Top-level screen
// ---------------------------------------------------------------------------------------------
@Composable
fun PhotoToRecipeScreen(
  onBack: () -> Unit,
  onSubmitted: () -> Unit,
  vm: PhotoToRecipeViewModel = hiltViewModel(),
) {
  val s by vm.state.collectAsState()
  val isPro by vm.entitlement.isPro.collectAsState()
  val ctx = LocalContext.current

  // Pop back to MySubmissionsScreen ~1.7s after the wax seal animation kicks in.
  LaunchedEffect(s.phase) {
    if (s.phase is PhotoToRecipePhase.Submitted) {
      delay(1700)
      onSubmitted()
    }
  }

  Scaffold(
    containerColor = Paper,
    topBar = {
      TopAppBar(
        title = { Text("Submit a recipe", fontWeight = FontWeight.Normal) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Paper),
      )
    },
  ) { padding ->
    Box(Modifier.padding(padding).fillMaxSize()) {
      when {
        !isPro -> ProGateView(reason = if (s.proGate) "Server confirmed Pro is required." else null)
        s.phase is PhotoToRecipePhase.Pick -> PickerView(
          error = s.error,
          onPickGallery = { uri -> vm.onPhotoPicked(ctx, uri) },
        )
        s.phase is PhotoToRecipePhase.Extracting -> ExtractingView(photoUri = s.photoUri)
        s.phase is PhotoToRecipePhase.Form -> FormView(
          photoUri = s.photoUri,
          form = s.form,
          submitting = s.submitting,
          error = s.error,
          notExtractable = s.notExtractable,
          onSetTitle = vm::setTitle,
          onSetCuisine = vm::setCuisine,
          onSetContentType = vm::setContentType,
          onServingsDelta = vm::setServings,
          onTimeDelta = vm::setTimeMinutes,
          onIngredientChange = vm::setIngredient,
          onAddIngredient = vm::addIngredient,
          onRemoveIngredient = vm::removeIngredient,
          onStepChange = vm::setStep,
          onAddStep = vm::addStep,
          onRemoveStep = vm::removeStep,
          onMoveStep = vm::moveStep,
          onSubmit = vm::submit,
        )
        s.phase is PhotoToRecipePhase.Submitted -> WaxSealConfirmation()
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Pro gate
// ---------------------------------------------------------------------------------------------
@Composable
private fun ProGateView(reason: String?) {
  LazyColumn(
    Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Text(
        "Submit a recipe",
        style = MaterialTheme.typography.headlineSmall,
        color = Ink,
        fontWeight = FontWeight.SemiBold,
      )
    }
    item {
      Text(
        "Recipe submission is a ${Brand.PRO_NAME} feature. Snap a photo of any recipe — printed, handwritten, screenshot — and we'll pull out the ingredients and steps for you to confirm.",
        style = MaterialTheme.typography.bodyMedium,
        color = InkSoft,
      )
    }
    if (!reason.isNullOrBlank()) {
      item { Text(reason, style = MaterialTheme.typography.labelSmall, color = InkFaint) }
    }
    item { ProUpgradeCard(vintageMode = false) }
  }
}

// ---------------------------------------------------------------------------------------------
// Photo picker
// ---------------------------------------------------------------------------------------------
@Composable
private fun PickerView(
  error: String?,
  onPickGallery: (Uri) -> Unit,
) {
  val ctx = LocalContext.current

  val galleryPicker = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? -> uri?.let(onPickGallery) }

  val cameraImageUri = remember { mutableStateOf<Uri?>(null) }
  val takePicture = rememberLauncherForActivityResult(
    ActivityResultContracts.TakePicture()
  ) { ok -> if (ok) cameraImageUri.value?.let(onPickGallery) }

  Column(
    Modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Text(
      "Photo of a recipe",
      style = MaterialTheme.typography.headlineSmall,
      color = Ink,
      fontWeight = FontWeight.SemiBold,
    )
    Text(
      "Take or pick one photo of a printed recipe, handwritten card, or screenshot. We'll extract the ingredients and steps — you'll review and confirm everything before submitting.",
      style = MaterialTheme.typography.bodyMedium,
      color = InkSoft,
    )
    Spacer(Modifier.height(8.dp))

    Button(
      onClick = {
        val uri = createCacheUri(ctx)
        cameraImageUri.value = uri
        takePicture.launch(uri)
      },
      modifier = Modifier.fillMaxWidth().height(56.dp),
      colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper),
      shape = RoundedCornerShape(4.dp),
    ) {
      Icon(Icons.Outlined.CameraAlt, null, modifier = Modifier.size(20.dp))
      Spacer(Modifier.width(10.dp))
      Text("Take a photo", fontWeight = FontWeight.SemiBold)
    }
    OutlinedButton(
      onClick = {
        galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
      },
      modifier = Modifier.fillMaxWidth().height(56.dp),
      shape = RoundedCornerShape(4.dp),
      border = BorderStroke(1.dp, InkFaint),
    ) {
      Icon(Icons.Outlined.PhotoLibrary, null, tint = Ink)
      Spacer(Modifier.width(10.dp))
      Text("Pick from gallery", color = Ink, fontWeight = FontWeight.SemiBold)
    }

    if (!error.isNullOrBlank()) {
      Spacer(Modifier.height(8.dp))
      Text(error, color = Terracotta, style = MaterialTheme.typography.bodyMedium)
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Extracting state
// ---------------------------------------------------------------------------------------------
@Composable
private fun ExtractingView(photoUri: Uri?) {
  Column(
    Modifier.fillMaxSize().padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    if (photoUri != null) {
      AsyncImage(
        model = photoUri,
        contentDescription = "Selected photo",
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .size(180.dp)
          .clip(RoundedCornerShape(12.dp))
          .border(1.dp, InkFaint, RoundedCornerShape(12.dp)),
      )
      Spacer(Modifier.height(20.dp))
    }
    CircularProgressIndicator(color = BrassBright, strokeWidth = 2.dp)
    Spacer(Modifier.height(16.dp))
    Text("Reading your recipe…", style = MaterialTheme.typography.titleMedium, color = Ink)
    Spacer(Modifier.height(4.dp))
    Text(
      "Usually 5-10 seconds. We'll only keep what we recognize.",
      style = MaterialTheme.typography.bodyMedium,
      color = InkSoft,
      textAlign = TextAlign.Center,
    )
  }
}

// ---------------------------------------------------------------------------------------------
// Editable form
// ---------------------------------------------------------------------------------------------
@Composable
private fun FormView(
  photoUri: Uri?,
  form: PhotoToRecipeForm,
  submitting: Boolean,
  error: String?,
  notExtractable: Boolean,
  onSetTitle: (String) -> Unit,
  onSetCuisine: (String) -> Unit,
  onSetContentType: (String) -> Unit,
  onServingsDelta: (Int) -> Unit,
  onTimeDelta: (Int) -> Unit,
  onIngredientChange: (Int, FormIngredient) -> Unit,
  onAddIngredient: () -> Unit,
  onRemoveIngredient: (Int) -> Unit,
  onStepChange: (Int, String) -> Unit,
  onAddStep: () -> Unit,
  onRemoveStep: (Int) -> Unit,
  onMoveStep: (Int, Int) -> Unit,
  onSubmit: () -> Unit,
) {
  LazyColumn(
    Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      photoUri?.let {
        AsyncImage(
          model = it, contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .fillMaxWidth().height(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, InkFaint, RoundedCornerShape(12.dp)),
        )
      }
    }
    if (notExtractable) {
      item {
        Surface(
          color = Paper2, shape = RoundedCornerShape(8.dp),
          border = BorderStroke(1.dp, BrassBright.copy(alpha = 0.5f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            "We couldn't read a recipe from that photo. You can still fill out the form below by hand.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
            modifier = Modifier.padding(12.dp),
          )
        }
      }
    }

    item {
      OutlinedTextField(
        value = form.title,
        onValueChange = onSetTitle,
        label = { Text("Recipe title", color = InkMuted) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
      )
    }

    item { SectionLabel("Type") }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CONTENT_TYPE_CHOICES.forEach { (key, label) ->
          val on = form.contentType == key
          FilterChip(
            selected = on,
            onClick = { onSetContentType(key) },
            label = { Text(label) },
            colors = FilterChipDefaults.filterChipColors(
              containerColor = Paper2,
              selectedContainerColor = Ink,
              labelColor = Ink,
              selectedLabelColor = Paper,
            ),
          )
        }
      }
    }

    item { SectionLabel("Cuisine") }
    item {
      ChipGrid(
        options = CUISINE_CHOICES,
        selected = form.cuisine,
        onPick = onSetCuisine,
        labelFor = { it.replaceFirstChar { c -> c.uppercase() }.replace("-", " ") },
      )
    }

    item {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Stepper(
          label = "Servings",
          value = form.servings?.toString() ?: "—",
          onMinus = { onServingsDelta(-1) },
          onPlus = { onServingsDelta(+1) },
          modifier = Modifier.weight(1f),
        )
        Stepper(
          label = "Time (min)",
          value = form.timeMinutes?.toString() ?: "—",
          onMinus = { onTimeDelta(-1) },
          onPlus = { onTimeDelta(+1) },
          modifier = Modifier.weight(1f),
        )
      }
    }

    item { SectionLabel("Ingredients", "Pick from the list — no freeform names.") }

    itemsIndexed(form.ingredients) { idx, ing ->
      IngredientRow(
        ing = ing,
        onChange = { onIngredientChange(idx, it) },
        onRemove = if (form.ingredients.size > 1) ({ onRemoveIngredient(idx) }) else null,
      )
    }

    item {
      OutlinedButton(
        onClick = onAddIngredient,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, InkFaint),
      ) {
        Icon(Icons.Outlined.Add, null, tint = Ink)
        Spacer(Modifier.width(6.dp))
        Text("Add ingredient", color = Ink)
      }
    }

    item { SectionLabel("Steps", "One action per step. Free text.") }

    itemsIndexed(form.steps) { idx, text ->
      StepRow(
        index = idx + 1,
        text = text,
        canMoveUp = idx > 0,
        canMoveDown = idx < form.steps.size - 1,
        onChange = { onStepChange(idx, it) },
        onUp = { onMoveStep(idx, -1) },
        onDown = { onMoveStep(idx, +1) },
        onDelete = if (form.steps.size > 1) ({ onRemoveStep(idx) }) else null,
      )
    }
    item {
      OutlinedButton(
        onClick = onAddStep,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, InkFaint),
      ) {
        Icon(Icons.Outlined.Add, null, tint = Ink)
        Spacer(Modifier.width(6.dp))
        Text("Add step", color = Ink)
      }
    }

    if (!error.isNullOrBlank()) {
      item { Text(error, color = Terracotta, style = MaterialTheme.typography.bodyMedium) }
    }

    item {
      Button(
        onClick = onSubmit,
        enabled = !submitting,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = Paper),
        shape = RoundedCornerShape(4.dp),
      ) {
        if (submitting) {
          CircularProgressIndicator(Modifier.size(18.dp), color = Paper, strokeWidth = 2.dp)
        } else {
          Text("Submit recipe", fontWeight = FontWeight.SemiBold)
        }
      }
    }
    item { Spacer(Modifier.height(40.dp)) }
  }
}

@Composable
private fun SectionLabel(title: String, hint: String? = null) {
  Column {
    Text(title, style = MaterialTheme.typography.titleMedium, color = Ink, fontWeight = FontWeight.SemiBold)
    if (hint != null) Text(hint, style = MaterialTheme.typography.labelSmall, color = InkMuted)
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGrid(
  options: List<String>,
  selected: String,
  onPick: (String) -> Unit,
  labelFor: (String) -> String,
) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    for (opt in options) {
      val on = opt == selected
      FilterChip(
        selected = on,
        onClick = { onPick(if (on) "" else opt) },
        label = { Text(labelFor(opt)) },
        colors = FilterChipDefaults.filterChipColors(
          containerColor = Paper2,
          selectedContainerColor = Ink,
          labelColor = Ink,
          selectedLabelColor = Paper,
        ),
      )
    }
  }
}

@Composable
private fun Stepper(
  label: String,
  value: String,
  onMinus: () -> Unit,
  onPlus: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = InkMuted)
    Spacer(Modifier.height(4.dp))
    Row(
      Modifier
        .fillMaxWidth()
        .height(48.dp)
        .border(1.dp, InkFaint, RoundedCornerShape(4.dp)),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onMinus, modifier = Modifier.weight(1f)) {
        Icon(Icons.Outlined.Remove, contentDescription = "Decrease $label", tint = Ink)
      }
      Text(
        value,
        modifier = Modifier.weight(1.2f),
        textAlign = TextAlign.Center,
        color = Ink,
        fontWeight = FontWeight.SemiBold,
      )
      IconButton(onClick = onPlus, modifier = Modifier.weight(1f)) {
        Icon(Icons.Outlined.Add, contentDescription = "Increase $label", tint = Ink)
      }
    }
  }
}

@Composable
private fun IngredientRow(
  ing: FormIngredient,
  onChange: (FormIngredient) -> Unit,
  onRemove: (() -> Unit)?,
) {
  Column(
    Modifier
      .fillMaxWidth()
      .border(1.dp, InkFaint.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
      .padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    // Searchable ingredient dropdown — the hero control. No freeform.
    SearchableIngredientPicker(
      selected = ing.canonical,
      onPick = { slug -> onChange(ing.copy(canonical = slug)) },
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
        value = ing.quantity,
        onValueChange = { v ->
          // Numeric-only — strip anything but digits and a single dot.
          val cleaned = v.filter { it.isDigit() || it == '.' }.let { s ->
            val firstDot = s.indexOf('.')
            if (firstDot < 0) s else s.substring(0, firstDot + 1) + s.substring(firstDot + 1).replace(".", "")
          }.take(6)
          onChange(ing.copy(quantity = cleaned))
        },
        label = { Text("Qty", color = InkMuted) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(4.dp),
      )
      Box(modifier = Modifier.weight(1.2f)) {
        UnitDropdown(
          selected = ing.unit,
          onPick = { u -> onChange(ing.copy(unit = u)) },
        )
      }
      if (onRemove != null) {
        IconButton(onClick = onRemove) {
          Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove ingredient", tint = InkFaint)
        }
      } else {
        Spacer(Modifier.size(40.dp))
      }
    }
  }
}

@Composable
private fun SearchableIngredientPicker(
  selected: String,
  onPick: (String) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  var query by remember(selected) { mutableStateOf(if (selected.isBlank()) "" else prettifySlug(selected)) }

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = !expanded },
  ) {
    OutlinedTextField(
      value = query,
      onValueChange = { v ->
        query = v
        expanded = true
      },
      label = { Text("Ingredient", color = InkMuted) },
      singleLine = true,
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      modifier = Modifier
        .menuAnchor()
        .fillMaxWidth(),
      shape = RoundedCornerShape(4.dp),
    )
    val q = query.trim().lowercase().replace(' ', '_')
    val filtered = if (q.isBlank()) INGREDIENT_SLUGS
      else INGREDIENT_SLUGS.filter { it.contains(q) || prettifySlug(it).lowercase().contains(query.trim().lowercase()) }

    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      modifier = Modifier.background(Paper2),
    ) {
      if (filtered.isEmpty()) {
        DropdownMenuItem(
          text = { Text("No matches — try shorter text", color = InkMuted) },
          onClick = { },
          enabled = false,
        )
      } else {
        // Cap the list shown to keep rendering snappy on the older devices we support.
        filtered.take(80).forEach { slug ->
          DropdownMenuItem(
            text = { Text(prettifySlug(slug), color = Ink) },
            onClick = {
              onPick(slug)
              query = prettifySlug(slug)
              expanded = false
            },
          )
        }
      }
    }
  }
}

@Composable
private fun UnitDropdown(
  selected: String,
  onPick: (String) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = !expanded },
  ) {
    OutlinedTextField(
      value = if (selected.isBlank()) "" else selected,
      onValueChange = { /* read-only */ },
      readOnly = true,
      label = { Text("Unit", color = InkMuted) },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      modifier = Modifier
        .menuAnchor()
        .fillMaxWidth(),
      shape = RoundedCornerShape(4.dp),
    )
    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      modifier = Modifier.background(Paper2),
    ) {
      DropdownMenuItem(text = { Text("(none)", color = InkMuted) }, onClick = {
        onPick("")
        expanded = false
      })
      UNIT_CHOICES.forEach { u ->
        DropdownMenuItem(text = { Text(u, color = Ink) }, onClick = {
          onPick(u)
          expanded = false
        })
      }
    }
  }
}

@Composable
private fun StepRow(
  index: Int,
  text: String,
  canMoveUp: Boolean,
  canMoveDown: Boolean,
  onChange: (String) -> Unit,
  onUp: () -> Unit,
  onDown: () -> Unit,
  onDelete: (() -> Unit)?,
) {
  Column(
    Modifier
      .fillMaxWidth()
      .border(1.dp, InkFaint.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
      .padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        "Step $index",
        style = MaterialTheme.typography.labelMedium,
        color = BrassBright,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.weight(1f),
      )
      IconButton(onClick = onUp, enabled = canMoveUp) {
        Icon(Icons.Outlined.ArrowUpward, "Move up", tint = if (canMoveUp) Ink else InkFaint.copy(alpha = 0.3f))
      }
      IconButton(onClick = onDown, enabled = canMoveDown) {
        Icon(Icons.Outlined.ArrowDownward, "Move down", tint = if (canMoveDown) Ink else InkFaint.copy(alpha = 0.3f))
      }
      if (onDelete != null) {
        IconButton(onClick = onDelete) {
          Icon(Icons.Outlined.DeleteOutline, "Delete", tint = InkFaint)
        }
      }
    }
    OutlinedTextField(
      value = text,
      onValueChange = onChange,
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 80.dp),
      shape = RoundedCornerShape(4.dp),
      placeholder = { Text("Describe the action…", color = InkMuted) },
    )
  }
}

// ---------------------------------------------------------------------------------------------
// Wax-seal success animation. Mirrors BetaFeedbackSheet.WaxSealConfirmation, but renders
// "RECIPE LOGGED" so the brand voice stays consistent.
// ---------------------------------------------------------------------------------------------
@Composable
private fun WaxSealConfirmation() {
  var phase by remember { mutableIntStateOf(0) }
  LaunchedEffect(Unit) {
    phase = 1
    delay(450)
    phase = 2
  }
  val sealScale by animateFloatAsState(
    targetValue = if (phase >= 1) 1f else 0.4f,
    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
    label = "seal-scale",
  )
  val sealAlpha by animateFloatAsState(
    targetValue = if (phase >= 1) 1f else 0f,
    animationSpec = tween(220, easing = FastOutSlowInEasing),
    label = "seal-alpha",
  )
  val sealRotation by animateFloatAsState(
    targetValue = if (phase >= 1) 0f else -14f,
    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
    label = "seal-rotation",
  )
  val textAlpha by animateFloatAsState(
    targetValue = if (phase >= 2) 1f else 0f,
    animationSpec = tween(360, delayMillis = 80, easing = FastOutSlowInEasing),
    label = "text-alpha",
  )

  Column(
    Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 56.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Box(
      modifier = Modifier
        .size(96.dp)
        .scale(sealScale)
        .rotate(sealRotation)
        .alpha(sealAlpha)
        .background(BrassBright, CircleShape)
        .border(2.dp, BrassBright.copy(alpha = 0.35f), CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.Outlined.Check, contentDescription = null, tint = Paper, modifier = Modifier.size(44.dp))
    }
    Spacer(Modifier.height(28.dp))
    Text(
      "RECIPE LOGGED",
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.SemiBold,
      letterSpacing = 4.sp,
      fontSize = 13.sp,
      color = BrassBright,
      modifier = Modifier.alpha(textAlpha),
    )
    Spacer(Modifier.height(10.dp))
    Text(
      "in the review queue",
      style = MaterialTheme.typography.bodyMedium,
      color = InkSoft,
      textAlign = TextAlign.Center,
      modifier = Modifier.alpha(textAlpha),
    )
  }
}
