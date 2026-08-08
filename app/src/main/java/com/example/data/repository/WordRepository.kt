package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
import com.example.data.local.PreloadedWords
import com.example.data.local.WordDao
import com.example.data.local.WordEntity
import com.example.data.model.WordDetail
import com.example.data.remote.FreeDictionaryApi
import com.example.data.remote.GeminiApiClient
import com.example.data.remote.GeminiContent
import com.example.data.remote.GeminiGenerationConfig
import com.example.data.remote.GeminiPart
import com.example.data.remote.GeminiRequest
import com.example.data.remote.GeminiWordJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class WordRepository(private val wordDao: WordDao) {

    val searchHistory: Flow<List<WordDetail>> = wordDao.getSearchHistory().map { list ->
        list.map { it.toWordDetail() }
    }

    val bookmarkedWords: Flow<List<WordDetail>> = wordDao.getBookmarkedWords().map { list ->
        list.map { it.toWordDetail() }
    }

    suspend fun getWord(queryWord: String): Result<WordDetail> = withContext(Dispatchers.IO) {
        val cleanWord = queryWord.lowercase().trim()
        if (cleanWord.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Word cannot be empty"))
        }

        // 1. Check local Room DB
        val cached = wordDao.getWord(cleanWord)
        if (cached != null && cached.simpleDefinition.isNotBlank()) {
            // Update timestamp for history order
            val updatedEntity = cached.copy(searchedAt = System.currentTimeMillis())
            wordDao.insertWord(updatedEntity)
            return@withContext Result.success(updatedEntity.toWordDetail())
        }

        // 2. Check Gemini AI if API Key is configured
        if (GeminiApiClient.hasValidApiKey()) {
            try {
                val geminiResult = fetchFromGemini(cleanWord)
                if (geminiResult != null) {
                    val entity = WordEntity.fromWordDetail(geminiResult, isBookmarked = cached?.isBookmarked ?: false)
                    wordDao.insertWord(entity)
                    return@withContext Result.success(geminiResult)
                }
            } catch (e: Exception) {
                Log.e("WordRepository", "Gemini API error", e)
            }
        }

        // 3. Fallback to Free Dictionary API
        try {
            val freeDictResult = fetchFromFreeDictionary(cleanWord, cached?.isBookmarked ?: false)
            if (freeDictResult != null) {
                val entity = WordEntity.fromWordDetail(freeDictResult, isBookmarked = cached?.isBookmarked ?: false)
                wordDao.insertWord(entity)
                return@withContext Result.success(freeDictResult)
            }
        } catch (e: Exception) {
            Log.e("WordRepository", "Free Dictionary API error", e)
        }

        // 4. Fallback to Preloaded Dataset
        val preloaded = PreloadedWords.find(cleanWord)
        if (preloaded != null) {
            val entity = WordEntity.fromWordDetail(preloaded, isBookmarked = cached?.isBookmarked ?: false)
            wordDao.insertWord(entity)
            return@withContext Result.success(preloaded)
        }

        return@withContext Result.failure(Exception("Could not find definition for '$cleanWord'. Please check spelling."))
    }

    private suspend fun fetchFromGemini(word: String): WordDetail? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val prompt = """
            You are an expert English dictionary assistant focused on explaining English words simply.
            Analyze the English word "$word" and provide a structured JSON response.
            Focus on simple explanations, relatable everyday examples, and practical synonyms/antonyms.

            JSON Schema:
            {
              "word": "$word",
              "phonetic": "/IPA pronunciation/",
              "partOfSpeech": "Noun / Verb / Adjective / Adverb",
              "simpleDefinition": "A clear, easy-to-understand explanation in simple English.",
              "cefrLevel": "A1 • Beginner / A2 • Elementary / B1 • Intermediate / B2 • Upper-Intermediate / C1 • Advanced / C2 • Proficiency",
              "simpleExamples": [
                "Everyday example sentence 1 using $word.",
                "Everyday example sentence 2 using $word."
              ],
              "synonyms": ["synonym1", "synonym2", "synonym3", "synonym4"],
              "antonyms": ["antonym1", "antonym2", "antonym3"],
              "memoryTrick": "A short, memorable trick or hook to help remember this word.",
              "usageTip": "A practical note on when or how to use this word in real life."
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            generationConfig = GeminiGenerationConfig(temperature = 0.2f, responseMimeType = "application/json")
        )

        val response = GeminiApiClient.service.generateContent(apiKey, request)
        val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: return null

        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(GeminiWordJson::class.java)
        val parsed = adapter.fromJson(jsonText) ?: return null

        return WordDetail(
            word = parsed.word ?: word,
            phonetic = parsed.phonetic ?: "",
            partOfSpeech = parsed.partOfSpeech ?: "Noun",
            simpleDefinition = parsed.simpleDefinition ?: "",
            cefrLevel = parsed.cefrLevel ?: "B1 • Intermediate",
            simpleExamples = parsed.simpleExamples ?: emptyList(),
            synonyms = parsed.synonyms ?: emptyList(),
            antonyms = parsed.antonyms ?: emptyList(),
            memoryTrick = parsed.memoryTrick ?: "",
            usageTip = parsed.usageTip ?: "",
            source = "Gemini AI"
        )
    }

    private suspend fun fetchFromFreeDictionary(word: String, isBookmarked: Boolean): WordDetail? {
        val entries = FreeDictionaryApi.service.getWordInfo(word)
        val entry = entries.firstOrNull() ?: return null

        val phonetic = entry.phonetic ?: entry.phonetics?.firstOrNull { !it.text.isNull_or_empty() }?.text ?: ""
        val meaning = entry.meanings?.firstOrNull()
        val partOfSpeech = meaning?.partOfSpeech?.replaceFirstChar { it.uppercase() } ?: "Noun"

        val defObj = meaning?.definitions?.firstOrNull()
        val rawDef = defObj?.definition ?: "Definition not available."

        val examplesList = mutableListOf<String>()
        defObj?.example?.let { if (it.isNotBlank()) examplesList.add(it) }
        entry.meanings?.forEach { m ->
            m.definitions?.forEach { d ->
                val ex = d.example
                if (!ex.isNullOrBlank() && examplesList.size < 3 && !examplesList.contains(ex)) {
                    examplesList.add(ex)
                }
            }
        }
        if (examplesList.isEmpty()) {
            examplesList.add("Using $word in daily conversation helps improve your English.")
        }

        val synonymsList = mutableSetOf<String>()
        meaning?.synonyms?.let { synonymsList.addAll(it) }
        defObj?.synonyms?.let { synonymsList.addAll(it) }
        entry.meanings?.forEach { m -> m.synonyms?.let { synonymsList.addAll(it) } }

        val antonymsList = mutableSetOf<String>()
        meaning?.antonyms?.let { antonymsList.addAll(it) }
        defObj?.antonyms?.let { antonymsList.addAll(it) }
        entry.meanings?.forEach { m -> m.antonyms?.let { antonymsList.addAll(it) } }

        return WordDetail(
            word = entry.word ?: word,
            phonetic = phonetic,
            partOfSpeech = partOfSpeech,
            simpleDefinition = rawDef,
            cefrLevel = "B1 • Intermediate",
            simpleExamples = examplesList.take(3),
            synonyms = synonymsList.take(6).toList(),
            antonyms = antonymsList.take(6).toList(),
            memoryTrick = "Practice saying '$word' in a sentence to build muscle memory!",
            usageTip = "Commonly used as a $partOfSpeech in everyday English.",
            isBookmarked = isBookmarked,
            source = "Free Dictionary"
        )
    }

    private fun String?.isNull_or_empty(): Boolean = this.isNullOrEmpty()
    private fun String?.isNull_or_blank(): Boolean = this.isNullOrBlank()

    suspend fun toggleBookmark(word: String, currentBookmarked: Boolean) = withContext(Dispatchers.IO) {
        wordDao.updateBookmark(word.lowercase().trim(), !currentBookmarked)
    }

    suspend fun deleteWord(word: String) = withContext(Dispatchers.IO) {
        wordDao.deleteWord(word.lowercase().trim())
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        wordDao.clearHistory()
    }
}
