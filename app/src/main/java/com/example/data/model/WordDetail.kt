package com.example.data.model

data class WordDetail(
    val word: String,
    val phonetic: String = "",
    val partOfSpeech: String = "Noun",
    val simpleDefinition: String = "",
    val cefrLevel: String = "B1 • Intermediate",
    val simpleExamples: List<String> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val memoryTrick: String = "",
    val usageTip: String = "",
    val isBookmarked: Boolean = false,
    val source: String = "Dictionary"
)
