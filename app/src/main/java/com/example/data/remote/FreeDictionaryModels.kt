package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FreeDictionaryResponse(
    val word: String?,
    val phonetic: String?,
    val phonetics: List<PhoneticDto>?,
    val meanings: List<MeaningDto>?
)

@JsonClass(generateAdapter = true)
data class PhoneticDto(
    val text: String?,
    val audio: String?
)

@JsonClass(generateAdapter = true)
data class MeaningDto(
    val partOfSpeech: String?,
    val definitions: List<DefinitionDto>?,
    val synonyms: List<String>?,
    val antonyms: List<String>?
)

@JsonClass(generateAdapter = true)
data class DefinitionDto(
    val definition: String?,
    val example: String?,
    val synonyms: List<String>?,
    val antonyms: List<String>?
)
