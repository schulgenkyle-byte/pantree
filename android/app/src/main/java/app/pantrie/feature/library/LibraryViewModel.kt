package app.pantrie.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.network.PantrieApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LibraryUi {
  data object Loading : LibraryUi
  data class Loaded(val books: List<Book>) : LibraryUi
  data class Error(val message: String) : LibraryUi
}

sealed interface BookUi {
  data object Loading : BookUi
  data class Loaded(val book: Book, val chapters: List<Chapter>) : BookUi
  data class Error(val message: String) : BookUi
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
  private val api: PantrieApi,
) : ViewModel() {

  private val _state = MutableStateFlow<LibraryUi>(LibraryUi.Loading)
  val state: StateFlow<LibraryUi> = _state.asStateFlow()

  private val _bookState = MutableStateFlow<BookUi>(BookUi.Loading)
  val bookState: StateFlow<BookUi> = _bookState.asStateFlow()

  private val _toast = MutableStateFlow<String?>(null)
  val toast: StateFlow<String?> = _toast.asStateFlow()

  fun load() {
    _state.value = LibraryUi.Loading
    viewModelScope.launch {
      runCatching { api.getLibrary() }
        .onSuccess { _state.value = LibraryUi.Loaded(it.books) }
        .onFailure { _state.value = LibraryUi.Error(it.message ?: "failed to load") }
    }
  }

  fun loadBook(bookId: String) {
    _bookState.value = BookUi.Loading
    viewModelScope.launch {
      runCatching { api.getBook(bookId) }
        .onSuccess { _bookState.value = BookUi.Loaded(it.book, it.chapters) }
        .onFailure { _bookState.value = BookUi.Error(it.message ?: "failed to load book") }
    }
  }

  fun createBook(title: String, description: String?, visibility: String, onCreated: (Book) -> Unit = {}) {
    viewModelScope.launch {
      runCatching { api.createBook(CreateBookRequest(title.trim(), description?.trim()?.takeIf { it.isNotEmpty() }, visibility)) }
        .onSuccess { newBook ->
          _toast.value = "Book created"
          load()
          onCreated(newBook)
        }
        .onFailure { _toast.value = "Could not create book: ${it.message}" }
    }
  }

  fun renameBook(bookId: String, title: String) {
    viewModelScope.launch {
      runCatching { api.updateBook(bookId, UpdateBookRequest(title = title.trim())) }
        .onSuccess { loadBook(bookId); load() }
        .onFailure { _toast.value = "Could not rename: ${it.message}" }
    }
  }

  fun setVisibility(bookId: String, visibility: String) {
    viewModelScope.launch {
      runCatching { api.updateBook(bookId, UpdateBookRequest(visibility = visibility)) }
        .onSuccess { loadBook(bookId); load() }
        .onFailure { _toast.value = "Could not change visibility: ${it.message}" }
    }
  }

  fun deleteBook(bookId: String, onDone: () -> Unit = {}) {
    viewModelScope.launch {
      runCatching { api.deleteBook(bookId) }
        .onSuccess { _toast.value = "Book deleted"; load(); onDone() }
        .onFailure { _toast.value = "Could not delete: ${it.message}" }
    }
  }

  fun createChapter(bookId: String, title: String) {
    viewModelScope.launch {
      runCatching { api.createChapter(bookId, CreateChapterRequest(title.trim())) }
        .onSuccess { loadBook(bookId) }
        .onFailure { _toast.value = "Could not create chapter: ${it.message}" }
    }
  }

  fun renameChapter(bookId: String, chapterId: String, title: String) {
    viewModelScope.launch {
      runCatching { api.updateChapter(bookId, chapterId, UpdateChapterRequest(title = title.trim())) }
        .onSuccess { loadBook(bookId) }
        .onFailure { _toast.value = "Could not rename chapter: ${it.message}" }
    }
  }

  fun deleteChapter(bookId: String, chapterId: String) {
    viewModelScope.launch {
      runCatching { api.deleteChapter(bookId, chapterId) }
        .onSuccess { loadBook(bookId) }
        .onFailure { _toast.value = "Could not delete chapter: ${it.message}" }
    }
  }

  fun addRecipe(bookId: String, chapterId: String, recipeId: String, note: String? = null) {
    viewModelScope.launch {
      runCatching { api.addRecipeToChapter(bookId, chapterId, AddRecipeRequest(recipeId, note?.takeIf { it.isNotBlank() })) }
        .onSuccess { _toast.value = "Added"; loadBook(bookId) }
        .onFailure { _toast.value = "Could not add: ${it.message}" }
    }
  }

  fun removeRecipe(bookId: String, chapterId: String, recipeId: String) {
    viewModelScope.launch {
      runCatching { api.removeRecipeFromChapter(bookId, chapterId, recipeId) }
        .onSuccess { loadBook(bookId) }
        .onFailure { _toast.value = "Could not remove: ${it.message}" }
    }
  }

  fun fork(sourceBookId: String, onForked: (Book) -> Unit = {}) {
    viewModelScope.launch {
      runCatching { api.forkBook(sourceBookId) }
        .onSuccess { _toast.value = "Forked into your library"; load(); onForked(it) }
        .onFailure { _toast.value = "Could not fork: ${it.message}" }
    }
  }

  /**
   * Mint a 5-minute share token, then invoke the callback with a browser-friendly
   * export URL that includes the token. Used by BookDetailScreen so private-book
   * exports work via system browser intent (the browser doesn't carry our JWT).
   */
  fun exportWithShareToken(bookId: String, format: String, baseUrl: String, openUrl: (String) -> Unit) {
    viewModelScope.launch {
      runCatching { api.mintBookShareToken(bookId) }
        .onSuccess { resp ->
          val url = "${baseUrl.trimEnd('/')}/api/library/books/$bookId/export?format=$format&share=${resp.token}"
          openUrl(url)
        }
        .onFailure { _toast.value = "Could not mint share token: ${it.message}" }
    }
  }

  fun clearToast() { _toast.update { null } }
}
