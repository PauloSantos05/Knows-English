package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.WordDetail

@Entity(tableName = "words")
data class WordEntity(
    @PrimaryKey val word: String,
    val phonetic: String,
    val partOfSpeech: String,
    val simpleDefinition: String,
    val cefrLevel: String,
    val examplesJson: String, // Pipe or pipe-delimited string
    val synonymsJson: String,
    val antonymsJson: String,
    val memoryTrick: String,
    val usageTip: String,
    val isBookmarked: Boolean = false,
    val searchedAt: Long = System.currentTimeMillis()
) {
    fun toWordDetail(): WordDetail {
        return WordDetail(
            word = word,
            phonetic = phonetic,
            partOfSpeech = partOfSpeech,
            simpleDefinition = simpleDefinition,
            cefrLevel = cefrLevel,
            simpleExamples = if (examplesJson.isBlank()) emptyList() else examplesJson.split("|||"),
            synonyms = if (synonymsJson.isBlank()) emptyList() else synonymsJson.split("|||"),
            antonyms = if (antonymsJson.isBlank()) emptyList() else antonymsJson.split("|||"),
            memoryTrick = memoryTrick,
            usageTip = usageTip,
            isBookmarked = isBookmarked
        )
    }

    companion object {
        fun fromWordDetail(detail: WordDetail, isBookmarked: Boolean = detail.isBookmarked): WordEntity {
            return WordEntity(
                word = detail.word.lowercase().trim(),
                phonetic = detail.phonetic,
                partOfSpeech = detail.partOfSpeech,
                simpleDefinition = detail.simpleDefinition,
                cefrLevel = detail.cefrLevel,
                examplesJson = detail.simpleExamples.joinToString("|||"),
                synonymsJson = detail.synonyms.joinToString("|||"),
                antonymsJson = detail.antonyms.joinToString("|||"),
                memoryTrick = detail.memoryTrick,
                usageTip = detail.usageTip,
                isBookmarked = isBookmarked,
                searchedAt = System.currentTimeMillis()
            )
        }
    }
}
