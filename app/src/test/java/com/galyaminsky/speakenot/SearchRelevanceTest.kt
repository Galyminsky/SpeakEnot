package com.galyaminsky.speakenot

import com.galyaminsky.speakenot.domain.MeaningSummary
import com.galyaminsky.speakenot.domain.Word
import com.galyaminsky.speakenot.domain.searchGroups
import com.galyaminsky.speakenot.domain.searchSuggestions
import org.junit.Assert.*
import org.junit.Test

class SearchRelevanceTest {
    // Fixed snapshots use terms and translations observed in Skyeng /words/search.
    @Test fun russianAppleRanksExactTranslationBeforePhraseAndPartial() {
        val words = listOf(
            word(1, "honey crisp", "яблоко в меду"),
            word(2, "apple", "яблоко", "яблоня"),
            word(3, "eating apple", "яблоко"),
            word(4, "bone of contention", "яблоко раздора"),
            word(5, "apple-shaped", "яблокообразный"),
        )
        assertEquals(listOf("apple", "eating apple", "honey crisp", "bone of contention", "apple-shaped", "apple"),
            flattened("яблоко", words))
        assertEquals(listOf("apple", "eating apple", "honey crisp", "bone of contention", "apple-shaped"), searchSuggestions("яблоко", words))
    }

    @Test fun russianMachineRanksExactTranslationAndKeepsSkyengOrderForTies() {
        val words = listOf(word(1, "amphibian", "амфибия"), word(2, "machine", "машина"),
            word(3, "mechanical", "механический"), word(4, "time machine", "машина времени"))
        assertEquals(listOf("machine", "time machine", "amphibian", "mechanical"), flattened("машина", words))
    }

    @Test fun russianDogRanksExactTranslationAcrossPartsOfSpeech() {
        val words = listOf(word(1, "bow-wow", "гав-гав", part = "n"),
            word(2, "dog", "собака", part = "n"), word(3, "genus canis", "собака", part = "ph"),
            word(4, "domestic dog", "собака", part = "n"), word(5, "pooch", "собака, дворняга", part = "n"))
        assertEquals(listOf("dog", "genus canis", "domestic dog", "pooch", "bow-wow"), flattened("собака", words))
    }

    @Test fun englishAppleRanksExactWordThenPhraseThenPartial() {
        val words = listOf(word(1, "applesauce", "яблочное пюре"), word(2, "apple", "яблоко", "яблоня"),
            word(3, "apple of one's eye", "зеница ока"), word(4, "applet", "апплет"))
        assertEquals(listOf("apple", "apple", "apple of one's eye", "applesauce", "applet"), flattened("apple", words))
    }

    @Test fun englishBookPreservesEveryMeaningAndOrdersPhrasesBeforeSubstrings() {
        val words = listOf(word(1, "bookstore", "книжный магазин"),
            word(2, "book", "книга", "бронировать"), word(3, "book a car", "брать автомобиль в аренду"),
            word(4, "booklet", "брошюра"))
        assertEquals(listOf("book", "book", "book a car", "bookstore", "booklet"), flattened("book", words))
        assertEquals(5, searchGroups("book", words).sumOf { it.items.size })
    }

    @Test fun englishRunKeepsSourceOrderWithinRelevanceTiers() {
        val words = listOf(word(1, "running", "постоянный"), word(2, "run", "бежать", "управлять"),
            word(3, "run out", "выдыхаться"), word(4, "run for it", "спасаться бегством"),
            word(5, "runner", "контрабандист"))
        assertEquals(listOf("run", "run", "run out", "run for it", "running", "runner"), flattened("run", words))
    }

    @Test fun suggestionsAreLimitedToFiveWithoutTruncatingResults() {
        val words = (1L..12L).map { word(it, "apple $it", "перевод $it") }
        assertEquals(5, searchSuggestions("apple", words).size)
        assertEquals(12, searchGroups("apple", words).sumOf { it.items.size })
        assertEquals(listOf("apple 1", "apple 2", "apple 3", "apple 4", "apple 5"), searchSuggestions("apple", words))
    }

    @Test fun duplicateSuggestionsKeepBestMatchingVariant() {
        val words = listOf(word(1, "DOG", "гав-гав"), word(2, "dog", "собака"), word(3, "pooch", "собака"))
        assertEquals(listOf("dog", "pooch"), searchSuggestions("собака", words))
    }

    private fun word(id: Long, text: String, vararg translations: String, part: String = "n") =
        Word(id, text, translations.mapIndexed { index, translation -> MeaningSummary(id * 100 + index, translation, null, part) })

    private fun flattened(query: String, words: List<Word>) = searchGroups(query, words).flatMap { group -> group.items.map { it.word } }
}
