package app.pantrie.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.pantrie.ui.theme.*
import coil.compose.AsyncImage

// =================================================================================================
// LibraryScreen — top-level entry point. Lists every Book in the user's Library.
// =================================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
  onBack: () -> Unit,
  onOpenBook: (bookId: String) -> Unit,
  vm: LibraryViewModel = hiltViewModel(),
) {
  val state by vm.state.collectAsState()
  val toast by vm.toast.collectAsState()
  var showCreateSheet by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) { vm.load() }

  Scaffold(
    containerColor = Paper,
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("Your Library", fontFamily = SerifDisplay, fontSize = 22.sp, color = Ink)
            Text("Books and chapters you create", fontSize = 12.sp, color = InkMuted)
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Ink)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Paper),
      )
    },
    floatingActionButton = {
      ExtendedFloatingActionButton(
        onClick = { showCreateSheet = true },
        containerColor = Terracotta,
        contentColor = Paper,
      ) {
        Icon(Icons.Outlined.Add, null)
        Spacer(Modifier.width(8.dp))
        Text("New Book")
      }
    },
  ) { padding ->
    Box(Modifier.fillMaxSize().padding(padding)) {
      when (val s = state) {
        is LibraryUi.Loading -> CenteredLoading()
        is LibraryUi.Error -> CenteredError(s.message) { vm.load() }
        is LibraryUi.Loaded -> {
          if (s.books.isEmpty()) {
            EmptyLibrary(onCreate = { showCreateSheet = true })
          } else {
            LazyColumn(
              contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              items(s.books, key = { it.id }) { book ->
                BookListRow(book = book, onClick = { onOpenBook(book.id) })
              }
              item { Spacer(Modifier.height(80.dp)) }
            }
          }
        }
      }
    }
  }

  if (showCreateSheet) {
    CreateBookSheet(
      onDismiss = { showCreateSheet = false },
      onCreate = { title, description, visibility ->
        vm.createBook(title, description, visibility) { newBook ->
          showCreateSheet = false
          onOpenBook(newBook.id)
        }
      },
    )
  }

  toast?.let {
    LaunchedEffect(it) {
      kotlinx.coroutines.delay(2200)
      vm.clearToast()
    }
    Snackbar(modifier = Modifier.padding(12.dp)) { Text(it) }
  }
}

@Composable
private fun BookListRow(book: Book, onClick: () -> Unit) {
  Surface(
    onClick = onClick,
    color = Paper2,
    border = androidx.compose.foundation.BorderStroke(1.dp, InkFaint.copy(alpha = 0.4f)),
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      Modifier.fillMaxWidth().padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Cover thumbnail or initials.
      Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(Beige),
        contentAlignment = Alignment.Center,
      ) {
        if (!book.coverImageUrl.isNullOrBlank()) {
          AsyncImage(model = book.coverImageUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
          Text(
            text = book.title.take(2).uppercase(),
            fontFamily = SerifDisplay,
            fontSize = 20.sp,
            color = Terracotta,
            fontWeight = FontWeight.Medium,
          )
        }
      }
      Spacer(Modifier.width(14.dp))
      Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            book.title,
            fontFamily = SerifDisplay,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = Ink,
            modifier = Modifier.weight(1f, fill = false),
          )
          if (book.isStandard == 1) {
            Spacer(Modifier.width(8.dp))
            Text("standard", fontSize = 9.sp, color = InkFaint, letterSpacing = 1.sp)
          }
        }
        Spacer(Modifier.height(4.dp))
        Text(
          buildString {
            append("${book.recipeCount} ")
            append(if (book.recipeCount == 1) "recipe" else "recipes")
            append(" · ")
            append(book.visibility)
          },
          fontSize = 12.sp,
          color = InkMuted,
        )
      }
      VisibilityIcon(book.visibility)
    }
  }
}

@Composable
private fun VisibilityIcon(visibility: String) {
  val (icon, color) = when (visibility) {
    "public" -> Icons.Outlined.Public to Olive
    "unlisted" -> Icons.Outlined.Public to InkSoft
    else -> Icons.Outlined.Lock to InkFaint
  }
  Icon(icon, contentDescription = visibility, tint = color, modifier = Modifier.size(18.dp))
}

@Composable
private fun EmptyLibrary(onCreate: () -> Unit) {
  Column(
    Modifier.fillMaxSize().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      "Your library is just getting started.",
      fontFamily = SerifDisplay,
      fontSize = 22.sp,
      color = Ink,
      textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Text(
      "Standard books for your saves and your own recipes are already there. Build your own books for genres, occasions, or anything you cook for.",
      fontSize = 14.sp,
      color = InkMuted,
      textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    Button(
      onClick = onCreate,
      colors = ButtonDefaults.buttonColors(containerColor = Terracotta),
    ) {
      Icon(Icons.Outlined.Add, null)
      Spacer(Modifier.width(8.dp))
      Text("Create your first book")
    }
  }
}

// =================================================================================================
// BookDetailScreen — view a Book, expand chapters, manage recipes
// =================================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
  bookId: String,
  onBack: () -> Unit,
  onOpenRecipe: (String) -> Unit,
  onExport: (String, String) -> Unit, // (bookId, format)
  vm: LibraryViewModel = hiltViewModel(),
) {
  val state by vm.bookState.collectAsState()
  val toast by vm.toast.collectAsState()
  var showRenameSheet by remember { mutableStateOf(false) }
  var showVisibilitySheet by remember { mutableStateOf(false) }
  var showAddChapterSheet by remember { mutableStateOf(false) }
  var showDeleteConfirm by remember { mutableStateOf(false) }

  LaunchedEffect(bookId) { vm.loadBook(bookId) }

  Scaffold(
    containerColor = Paper,
    topBar = {
      val title = (state as? BookUi.Loaded)?.book?.title ?: "Book"
      TopAppBar(
        title = { Text(title, fontFamily = SerifDisplay, fontSize = 18.sp, color = Ink) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Ink)
          }
        },
        actions = {
          val loaded = state as? BookUi.Loaded
          if (loaded != null) {
            if (loaded.book.isStandard == 0) {
              IconButton(onClick = { showRenameSheet = true }) {
                Icon(Icons.Outlined.Edit, null, tint = Ink)
              }
            }
            IconButton(onClick = { onExport(loaded.book.id, "markdown") }) {
              Icon(Icons.Outlined.Download, null, tint = Ink)
            }
            if (loaded.book.isStandard == 0) {
              IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Outlined.Delete, null, tint = Color(0xFFB04A3C))
              }
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Paper),
      )
    },
    floatingActionButton = {
      val loaded = state as? BookUi.Loaded
      if (loaded != null) {
        ExtendedFloatingActionButton(
          onClick = { showAddChapterSheet = true },
          containerColor = Terracotta,
          contentColor = Paper,
        ) {
          Icon(Icons.Outlined.Add, null)
          Spacer(Modifier.width(8.dp))
          Text("New Chapter")
        }
      }
    },
  ) { padding ->
    Box(Modifier.fillMaxSize().padding(padding)) {
      when (val s = state) {
        is BookUi.Loading -> CenteredLoading()
        is BookUi.Error -> CenteredError(s.message) { vm.loadBook(bookId) }
        is BookUi.Loaded -> {
          LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            item { BookHeader(s.book, onChangeVisibility = { showVisibilitySheet = true }) }
            items(s.chapters, key = { it.id }) { chapter ->
              ChapterAccordion(
                chapter = chapter,
                onOpenRecipe = onOpenRecipe,
                onRemoveRecipe = { rid -> vm.removeRecipe(bookId, chapter.id, rid) },
                onRenameChapter = { newTitle -> vm.renameChapter(bookId, chapter.id, newTitle) },
                onDeleteChapter = { vm.deleteChapter(bookId, chapter.id) },
              )
            }
            if (s.chapters.isEmpty()) {
              item {
                Text(
                  "No chapters yet. Tap New Chapter to organize this book.",
                  fontSize = 13.sp,
                  color = InkMuted,
                  modifier = Modifier.padding(16.dp),
                )
              }
            }
            item { Spacer(Modifier.height(80.dp)) }
          }
        }
      }
    }
  }

  // Sheets
  val loaded = state as? BookUi.Loaded
  if (showRenameSheet && loaded != null) {
    RenameSheet(
      currentTitle = loaded.book.title,
      onDismiss = { showRenameSheet = false },
      onSave = { newTitle ->
        vm.renameBook(bookId, newTitle)
        showRenameSheet = false
      },
    )
  }
  if (showVisibilitySheet && loaded != null) {
    VisibilitySheet(
      current = loaded.book.visibility,
      isStandard = loaded.book.isStandard == 1,
      onDismiss = { showVisibilitySheet = false },
      onSelect = { v ->
        vm.setVisibility(bookId, v)
        showVisibilitySheet = false
      },
    )
  }
  if (showAddChapterSheet) {
    SimpleTitleSheet(
      title = "New Chapter",
      placeholder = "e.g. Indian Desserts",
      onDismiss = { showAddChapterSheet = false },
      onSubmit = { t ->
        vm.createChapter(bookId, t)
        showAddChapterSheet = false
      },
    )
  }
  if (showDeleteConfirm && loaded != null) {
    AlertDialog(
      onDismissRequest = { showDeleteConfirm = false },
      title = { Text("Delete this book?") },
      text = {
        Text(
          if (loaded.book.visibility == "private")
            "This will permanently delete the book and all chapters."
          else
            "This is a public book. It will be archived from your library but stay reachable to anyone who already has the link.",
        )
      },
      confirmButton = {
        TextButton(onClick = {
          showDeleteConfirm = false
          vm.deleteBook(bookId, onDone = onBack)
        }) { Text("Delete", color = Color(0xFFB04A3C)) }
      },
      dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
    )
  }

  toast?.let {
    LaunchedEffect(it) {
      kotlinx.coroutines.delay(2200)
      vm.clearToast()
    }
  }
}

@Composable
private fun BookHeader(book: Book, onChangeVisibility: () -> Unit) {
  Column(
    Modifier.fillMaxWidth().background(Paper2, RoundedCornerShape(12.dp)).padding(16.dp),
  ) {
    if (!book.description.isNullOrBlank()) {
      Text(book.description, fontSize = 14.sp, color = InkSoft)
      Spacer(Modifier.height(10.dp))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      VisibilityIcon(book.visibility)
      Spacer(Modifier.width(6.dp))
      TextButton(onClick = onChangeVisibility) {
        Text(book.visibility, fontSize = 12.sp, color = InkSoft)
      }
      Spacer(Modifier.width(12.dp))
      Text(
        "${book.recipeCount} recipes",
        fontSize = 12.sp,
        color = InkMuted,
      )
      if (book.forkCount > 0) {
        Spacer(Modifier.width(12.dp))
        Text("${book.forkCount} forks", fontSize = 12.sp, color = InkMuted)
      }
    }
  }
}

@Composable
private fun ChapterAccordion(
  chapter: Chapter,
  onOpenRecipe: (String) -> Unit,
  onRemoveRecipe: (String) -> Unit,
  onRenameChapter: (String) -> Unit,
  onDeleteChapter: () -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  var showActions by remember { mutableStateOf(false) }
  var showRename by remember { mutableStateOf(false) }

  Surface(
    color = Paper2,
    border = androidx.compose.foundation.BorderStroke(1.dp, InkFaint.copy(alpha = 0.4f)),
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column {
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.weight(1f)) {
          Text(
            if (expanded) "−" else "+",
            fontSize = 18.sp,
            color = InkSoft,
            modifier = Modifier.width(20.dp),
          )
          Text(
            chapter.title,
            fontFamily = SerifDisplay,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Ink,
            modifier = Modifier.weight(1f),
          )
          Text(
            "${chapter.recipeCount}",
            fontSize = 13.sp,
            color = InkMuted,
          )
        }
        IconButton(onClick = { showActions = true }, modifier = Modifier.size(36.dp)) {
          Icon(Icons.Outlined.Edit, null, tint = InkFaint, modifier = Modifier.size(16.dp))
        }
      }
      if (expanded) {
        if (chapter.recipes.isEmpty()) {
          Text(
            "No recipes here yet. Add one from any recipe screen.",
            fontSize = 12.sp,
            color = InkMuted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          )
        } else {
          chapter.recipes.forEach { r ->
            RecipeRow(r, onOpen = { onOpenRecipe(r.recipeId) }, onRemove = { onRemoveRecipe(r.recipeId) })
          }
        }
        Spacer(Modifier.height(8.dp))
      }
    }
  }

  if (showActions) {
    AlertDialog(
      onDismissRequest = { showActions = false },
      title = { Text(chapter.title) },
      text = { Text("What would you like to do?") },
      confirmButton = {
        TextButton(onClick = {
          showActions = false
          showRename = true
        }) { Text("Rename") }
      },
      dismissButton = {
        TextButton(onClick = {
          showActions = false
          onDeleteChapter()
        }) { Text("Delete chapter", color = Color(0xFFB04A3C)) }
      },
    )
  }

  if (showRename) {
    SimpleTitleSheet(
      title = "Rename chapter",
      placeholder = chapter.title,
      onDismiss = { showRename = false },
      onSubmit = {
        onRenameChapter(it)
        showRename = false
      },
    )
  }
}

@Composable
private fun RecipeRow(r: ChapterRecipe, onOpen: () -> Unit, onRemove: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)).background(Beige),
      contentAlignment = Alignment.Center,
    ) {
      if (!r.imageUrl.isNullOrBlank()) {
        AsyncImage(model = r.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
      } else {
        Text(r.title.take(1).uppercase(), color = Terracotta, fontSize = 14.sp)
      }
    }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      TextButton(onClick = onOpen, contentPadding = PaddingValues(0.dp)) {
        Text(r.title, fontSize = 14.sp, color = Ink, modifier = Modifier.weight(1f, false))
      }
      val sub = listOfNotNull(
        r.cuisine,
        r.cookMinutes?.let { "$it min" },
      ).joinToString(" · ")
      if (sub.isNotEmpty()) {
        Text(sub, fontSize = 11.sp, color = InkMuted)
      }
      if (r.createdByUserId != null) {
        Text("yours, exportable", fontSize = 10.sp, color = Olive)
      }
    }
    IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
      Icon(Icons.Outlined.Delete, null, tint = InkFaint, modifier = Modifier.size(16.dp))
    }
  }
}

// =================================================================================================
// Sheets
// =================================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateBookSheet(
  onDismiss: () -> Unit,
  onCreate: (title: String, description: String?, visibility: String) -> Unit,
) {
  var title by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var visibility by remember { mutableStateOf("private") }

  ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Paper) {
    Column(Modifier.padding(20.dp)) {
      Text("Create a Book", fontFamily = SerifDisplay, fontSize = 22.sp, color = Ink)
      Spacer(Modifier.height(4.dp))
      Text(
        "Books hold chapters of recipes. Name it after a genre, occasion, or anyone you cook for.",
        fontSize = 12.sp,
        color = InkMuted,
      )
      Spacer(Modifier.height(16.dp))
      OutlinedTextField(
        value = title,
        onValueChange = { if (it.length <= 80) title = it },
        label = { Text("Title") },
        placeholder = { Text("e.g. Indian Cookbook") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(12.dp))
      OutlinedTextField(
        value = description,
        onValueChange = { if (it.length <= 1200) description = it },
        label = { Text("Description (optional)") },
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
      )
      Spacer(Modifier.height(16.dp))
      Text("Who can see it?", fontSize = 13.sp, color = InkSoft)
      Spacer(Modifier.height(8.dp))
      VisibilityRadio(visibility) { visibility = it }
      Spacer(Modifier.height(20.dp))
      Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onDismiss) { Text("Cancel") }
        Spacer(Modifier.width(8.dp))
        Button(
          onClick = { onCreate(title, description, visibility) },
          enabled = title.trim().isNotEmpty(),
          colors = ButtonDefaults.buttonColors(containerColor = Terracotta),
        ) { Text("Create") }
      }
      Spacer(Modifier.height(20.dp))
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenameSheet(currentTitle: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
  var t by remember { mutableStateOf(currentTitle) }
  ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Paper) {
    Column(Modifier.padding(20.dp)) {
      Text("Rename book", fontFamily = SerifDisplay, fontSize = 18.sp, color = Ink)
      Spacer(Modifier.height(12.dp))
      OutlinedTextField(value = t, onValueChange = { if (it.length <= 80) t = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
      Spacer(Modifier.height(16.dp))
      Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onDismiss) { Text("Cancel") }
        Spacer(Modifier.width(8.dp))
        Button(onClick = { onSave(t) }, enabled = t.trim().isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = Terracotta)) {
          Text("Save")
        }
      }
      Spacer(Modifier.height(16.dp))
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisibilitySheet(
  current: String,
  isStandard: Boolean,
  onDismiss: () -> Unit,
  onSelect: (String) -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Paper) {
    Column(Modifier.padding(20.dp)) {
      Text("Who can see this book?", fontFamily = SerifDisplay, fontSize = 18.sp, color = Ink)
      if (isStandard) {
        Spacer(Modifier.height(8.dp))
        Text("Standard books stay private.", fontSize = 12.sp, color = InkMuted)
      }
      Spacer(Modifier.height(16.dp))
      VisibilityRadio(current, enabled = !isStandard) { onSelect(it) }
      Spacer(Modifier.height(16.dp))
      Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onDismiss) { Text("Done") }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleTitleSheet(
  title: String,
  placeholder: String,
  onDismiss: () -> Unit,
  onSubmit: (String) -> Unit,
) {
  var t by remember { mutableStateOf("") }
  ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Paper) {
    Column(Modifier.padding(20.dp)) {
      Text(title, fontFamily = SerifDisplay, fontSize = 18.sp, color = Ink)
      Spacer(Modifier.height(12.dp))
      OutlinedTextField(
        value = t,
        onValueChange = { if (it.length <= 80) t = it },
        placeholder = { Text(placeholder) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(16.dp))
      Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onDismiss) { Text("Cancel") }
        Spacer(Modifier.width(8.dp))
        Button(onClick = { onSubmit(t) }, enabled = t.trim().isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = Terracotta)) {
          Text("Save")
        }
      }
      Spacer(Modifier.height(16.dp))
    }
  }
}

@Composable
private fun VisibilityRadio(current: String, enabled: Boolean = true, onChange: (String) -> Unit) {
  val options = listOf(
    "private" to "Private — only you",
    "unlisted" to "Unlisted — anyone with the link",
    "public" to "Public — anyone can find and fork",
  )
  Column {
    options.forEach { (value, label) ->
      Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        RadioButton(
          selected = current == value,
          onClick = { if (enabled) onChange(value) },
          enabled = enabled,
          colors = RadioButtonDefaults.colors(selectedColor = Terracotta),
        )
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 13.sp, color = if (enabled) Ink else InkFaint)
      }
    }
  }
}

// =================================================================================================
// AddToBookSheet — embed from any recipe-context screen to pin the recipe into a chapter.
// =================================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToBookSheet(
  recipeId: String,
  onDismiss: () -> Unit,
  vm: LibraryViewModel = hiltViewModel(),
) {
  val state by vm.state.collectAsState()
  val bookState by vm.bookState.collectAsState()
  val toast by vm.toast.collectAsState()
  var pickedBookId by remember { mutableStateOf<String?>(null) }
  var showCreateSheet by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) { vm.load() }
  LaunchedEffect(pickedBookId) { pickedBookId?.let { vm.loadBook(it) } }
  LaunchedEffect(toast) {
    if (toast?.startsWith("Added") == true) {
      kotlinx.coroutines.delay(700)
      onDismiss()
    }
  }

  ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Paper) {
    Column(Modifier.padding(20.dp).heightIn(max = 560.dp)) {
      val pickedBookTitle = (state as? LibraryUi.Loaded)?.books?.firstOrNull { it.id == pickedBookId }?.title
      Text(
        text = if (pickedBookId == null) "Save to a book" else (pickedBookTitle ?: "Pick a chapter"),
        fontFamily = SerifDisplay,
        fontSize = 22.sp,
        color = Ink,
      )
      Spacer(Modifier.height(4.dp))
      Text(
        if (pickedBookId == null)
          "Pick a book in your Library, then a chapter."
        else
          "Pick a chapter to drop this recipe into.",
        fontSize = 12.sp,
        color = InkMuted,
      )
      Spacer(Modifier.height(16.dp))

      if (pickedBookId == null) {
        when (val s = state) {
          is LibraryUi.Loading -> CenteredLoading()
          is LibraryUi.Error -> CenteredError(s.message) { vm.load() }
          is LibraryUi.Loaded -> {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              items(s.books, key = { it.id }) { book ->
                BookListRow(book = book, onClick = { pickedBookId = book.id })
              }
              item {
                TextButton(onClick = { showCreateSheet = true }, modifier = Modifier.fillMaxWidth()) {
                  Icon(Icons.Outlined.Add, null, tint = Terracotta)
                  Spacer(Modifier.width(8.dp))
                  Text("Create a new book", color = Terracotta)
                }
              }
            }
          }
        }
      } else {
        when (val b = bookState) {
          is BookUi.Loading -> CenteredLoading()
          is BookUi.Error -> CenteredError(b.message) { pickedBookId?.let { vm.loadBook(it) } }
          is BookUi.Loaded -> {
            Column {
              TextButton(onClick = { pickedBookId = null }) {
                Text("Back to all books", color = InkSoft, fontSize = 13.sp)
              }
              Spacer(Modifier.height(4.dp))
              if (b.chapters.isEmpty()) {
                Text(
                  "This book has no chapters yet. Open the book and add one first.",
                  fontSize = 13.sp,
                  color = InkMuted,
                  modifier = Modifier.padding(12.dp),
                )
              } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  items(b.chapters, key = { it.id }) { chapter ->
                    Surface(
                      onClick = {
                        vm.addRecipe(b.book.id, chapter.id, recipeId)
                      },
                      color = Paper2,
                      border = androidx.compose.foundation.BorderStroke(1.dp, InkFaint.copy(alpha = 0.4f)),
                      shape = RoundedCornerShape(10.dp),
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        Text(
                          chapter.title,
                          fontFamily = SerifDisplay,
                          fontSize = 15.sp,
                          color = Ink,
                          modifier = Modifier.weight(1f),
                        )
                        Text("${chapter.recipeCount}", fontSize = 12.sp, color = InkMuted)
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

      Spacer(Modifier.height(20.dp))
    }
  }

  if (showCreateSheet) {
    CreateBookSheet(
      onDismiss = { showCreateSheet = false },
      onCreate = { title, description, visibility ->
        vm.createBook(title, description, visibility) { newBook ->
          showCreateSheet = false
          pickedBookId = newBook.id
        }
      },
    )
  }
}

@Composable
private fun CenteredLoading() {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(color = Terracotta)
  }
}

@Composable
private fun CenteredError(message: String, onRetry: () -> Unit) {
  Column(
    Modifier.fillMaxSize().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text("Could not load.", fontFamily = SerifDisplay, fontSize = 20.sp, color = Ink)
    Spacer(Modifier.height(8.dp))
    Text(message, fontSize = 12.sp, color = InkMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Terracotta)) {
      Text("Retry")
    }
  }
}
