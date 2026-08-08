package com.example.ui.viewmodel

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.PreloadedWords
import com.example.data.model.WordDetail
import com.example.data.repository.WordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

sealed class LookupUiState {
    object Idle : LookupUiState()
    object Loading : LookupUiState()
    data class Success(val wordDetail: WordDetail) : LookupUiState()
    data class Error(val message: String) : LookupUiState()
}

class WordViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val repository: WordRepository
    private var tts: TextToSpeech? = null
    private val _isTtsReady = MutableStateFlow(false)
    val isTtsReady: StateFlow<Boolean> = _isTtsReady.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _uiState = MutableStateFlow<LookupUiState>(LookupUiState.Idle)
    val uiState: StateFlow<LookupUiState> = _uiState.asStateFlow()

    val searchHistory: StateFlow<List<WordDetail>>
    val bookmarkedWords: StateFlow<List<WordDetail>>

    // Daily flashcards
    private val _dailyCardIndex = MutableStateFlow(0)
    val dailyCardIndex: StateFlow<Int> = _dailyCardIndex.asStateFlow()

    private val _isCardFlipped = MutableStateFlow(false)
    val isCardFlipped: StateFlow<Boolean> = _isCardFlipped.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = WordRepository(db.wordDao())

        searchHistory = repository.searchHistory.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        bookmarkedWords = repository.bookmarkedWords.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        tts = TextToSpeech(application, this)

        // Preload default word "Resilient" on first launch
        lookupWord("resilient")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                _isTtsReady.value = true
            }
        }
    }

    fun speakWord(text: String) {
        if (_isTtsReady.value && text.isNotBlank()) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "WordTTS_${System.currentTimeMillis()}")
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun lookupWord(wordToSearch: String = _searchQuery.value) {
        val clean = wordToSearch.trim()
        if (clean.isBlank()) return

        _searchQuery.value = clean
        _uiState.value = LookupUiState.Loading

        viewModelScope.launch {
            val result = repository.getWord(clean)
            result.fold(
                onSuccess = { detail ->
                    _uiState.value = LookupUiState.Success(detail)
                },
                onFailure = { error ->
                    _uiState.value = LookupUiState.Error(error.localizedMessage ?: "Failed to lookup word.")
                }
            )
        }
    }

    fun lookupRandomWord() {
        val randomWord = PreloadedWords.list.random()
        lookupWord(randomWord.word)
    }

    fun toggleBookmark(detail: WordDetail) {
        viewModelScope.launch {
            repository.toggleBookmark(detail.word, detail.isBookmarked)
            // If current UI state has this word, update UI state bookmark
            val current = _uiState.value
            if (current is LookupUiState.Success && current.wordDetail.word.equals(detail.word, ignoreCase = true)) {
                _uiState.value = LookupUiState.Success(current.wordDetail.copy(isBookmarked = !detail.isBookmarked))
            }
        }
    }

    fun deleteWordFromHistory(word: String) {
        viewModelScope.launch {
            repository.deleteWord(word)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun nextDailyCard() {
        _isCardFlipped.value = false
        _dailyCardIndex.value = (_dailyCardIndex.value + 1) % PreloadedWords.list.size
    }

    fun previousDailyCard() {
        _isCardFlipped.value = false
        val total = PreloadedWords.list.size
        _dailyCardIndex.value = (_dailyCardIndex.value - 1 + total) % total
    }

    fun toggleCardFlip() {
        _isCardFlipped.value = !_isCardFlipped.value
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
    }
}
