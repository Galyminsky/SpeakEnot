package com.galyaminsky.speakenot.domain

import java.util.Locale

enum class InputLanguage { EN, RU }

sealed interface QueryInput {
    data object Empty : QueryInput
    data object Invalid : QueryInput
    data class Valid(val text: String, val language: InputLanguage) : QueryInput
}

fun parseQuery(raw: String): QueryInput {
    val text = raw.trim().replace(Regex("\\s+"), " ")
    if (text.isEmpty()) return QueryInput.Empty
    val latin = text.any { it in 'a'..'z' || it in 'A'..'Z' }
    val cyrillic = text.any { it in '\u0400'..'\u04FF' }
    if (latin == cyrillic || text.any { it.isLetter() && it !in 'a'..'z' && it !in 'A'..'Z' && it !in '\u0400'..'\u04FF' }) return QueryInput.Invalid
    if (text.any { !it.isLetterOrDigit() && it != ' ' && it != '-' && it != '\'' && it != '’' }) return QueryInput.Invalid
    return QueryInput.Valid(text, if (latin) InputLanguage.EN else InputLanguage.RU)
}

data class SearchItem(val wordId: Long, val word: String, val meaning: MeaningSummary)
data class SearchGroup(val partOfSpeech: String?, val items: List<SearchItem>)

fun searchGroups(query: String, words: List<Word>): List<SearchGroup> {
    val language = (parseQuery(query) as? QueryInput.Valid)?.language ?: return emptyList()
    val ranked = words.flatMapIndexed { wordIndex, word ->
        word.meanings.mapIndexed { meaningIndex, meaning ->
            RankedItem(SearchItem(word.id, word.text, meaning),
                relevance(query, language, word.text, meaning.translation), wordIndex, meaningIndex)
        }
    }.sortedWith(compareBy<RankedItem> { it.rank }.thenBy { it.wordIndex }.thenBy { it.meaningIndex })
    // Group only adjacent meanings so the ranked Skyeng order stays intact.
    val groups = mutableListOf<SearchGroup>()
    for (entry in ranked) {
        val partOfSpeech = entry.item.meaning.partOfSpeech
        val previous = groups.lastOrNull()
        if (previous != null && previous.partOfSpeech == partOfSpeech) {
            groups[groups.lastIndex] = previous.copy(items = previous.items + entry.item)
        } else {
            groups += SearchGroup(partOfSpeech, listOf(entry.item))
        }
    }
    return groups
}

fun searchSuggestions(query: String, words: List<Word>): List<String> {
    val language = (parseQuery(query) as? QueryInput.Valid)?.language ?: return emptyList()
    return words.withIndex().map { (index, word) ->
        val rank = word.meanings.minOfOrNull { relevance(query, language, word.text, it.translation) }
            ?: relevance(query, language, word.text, null)
        Suggestion(word.text, rank, index)
    }.groupBy { it.text.lowercase(Locale.ROOT) }
        .values.map { entries -> entries.minWith(compareBy<Suggestion> { it.rank }.thenBy { it.index }) }
        .sortedWith(compareBy<Suggestion> { it.rank }.thenBy { it.index })
        .asSequence().map { it.text }
    .take(5)
    .toList()
}

private data class RankedItem(val item: SearchItem, val rank: Int, val wordIndex: Int, val meaningIndex: Int)
private data class Suggestion(val text: String, val rank: Int, val index: Int)

private fun relevance(query: String, language: InputLanguage, word: String, translation: String?): Int {
    val candidate = if (language == InputLanguage.EN) word else translation.orEmpty()
    val needle = query.trim().lowercase(Locale.ROOT)
    val haystack = candidate.trim().lowercase(Locale.ROOT)
    if (haystack == needle) return 0
    if (containsWholePhrase(haystack, needle)) return 1
    if (haystack.contains(needle)) return 2
    return 3
}

private fun containsWholePhrase(text: String, phrase: String): Boolean {
    var start = text.indexOf(phrase)
    while (start >= 0) {
        val end = start + phrase.length
        if ((start == 0 || !text[start - 1].isLetterOrDigit()) &&
            (end == text.length || !text[end].isLetterOrDigit())) return true
        start = text.indexOf(phrase, start + 1)
    }
    return false
}
