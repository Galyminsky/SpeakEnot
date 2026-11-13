package com.galyaminsky.speakenot

import com.galyaminsky.speakenot.data.remote.MeaningSummaryDto
import com.galyaminsky.speakenot.data.remote.toDomain
import com.galyaminsky.speakenot.domain.InputLanguage
import com.galyaminsky.speakenot.domain.MeaningSummary
import com.galyaminsky.speakenot.domain.QueryInput
import com.galyaminsky.speakenot.domain.Word
import com.galyaminsky.speakenot.domain.parseQuery
import com.galyaminsky.speakenot.domain.searchGroups
import com.galyaminsky.speakenot.domain.searchSuggestions
import org.junit.Assert.*
import org.junit.Test

class SearchLogicTest {
    @Test fun detectsEnglishRussianAndRejectsMixedOrUnsupportedInput() {
        assertEquals(QueryInput.Valid("good morning", InputLanguage.EN), parseQuery("  good   morning  "))
        assertEquals(QueryInput.Valid("доброе утро", InputLanguage.RU), parseQuery(" доброе\n утро "))
        assertEquals(QueryInput.Invalid, parseQuery("go привет"))
        assertEquals(QueryInput.Invalid, parseQuery("123"))
        assertEquals(QueryInput.Invalid, parseQuery("你好"))
        assertEquals(QueryInput.Empty, parseQuery(" \t "))
        assertEquals(QueryInput.Valid("mother-in-law", InputLanguage.EN), parseQuery("mother-in-law"))
    }

    @Test fun mapsPartOfSpeechAndGroupsEachMeaningWithExactMatchFirst() {
        assertEquals("n", MeaningSummaryDto(1, partOfSpeechCode = " n ").toDomain()?.partOfSpeech)
        val words = listOf(
            Word(1, "bookcase", listOf(MeaningSummary(11, "шкаф", null, "n"))),
            Word(2, "book", listOf(MeaningSummary(21, "книга", null, "n"), MeaningSummary(22, "бронировать", null, "v"))),
        )
        val groups = searchGroups("BOOK", words)
        assertEquals(listOf("n", "v", "n"), groups.map { it.partOfSpeech })
        assertEquals(listOf(21L), groups[0].items.map { it.meaning.id })
        assertEquals(22L, groups[1].items.single().meaning.id)
        assertEquals(listOf(11L), groups[2].items.map { it.meaning.id })
        assertEquals(3, groups.sumOf { it.items.size })
    }

    @Test fun suggestionsUseSearchWordsDeduplicateAndCapAtFive() {
        val words = (1L..8L).map { Word(it, "word$it", emptyList()) } + Word(9, "WORD1", emptyList())
        assertEquals(listOf("word3", "word1", "word2", "word4", "word5"), searchSuggestions("word3", words))
        assertTrue(searchSuggestions("x", emptyList()).isEmpty())
        assertTrue(searchGroups("x", words).isEmpty())
    }
}
