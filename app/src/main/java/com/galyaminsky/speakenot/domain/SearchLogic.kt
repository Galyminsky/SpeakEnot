package com.galyaminsky.speakenot.domain

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
    val ordered = words.flatMap { word -> word.meanings.map { SearchItem(word.id, word.text, it) } }
        .sortedWith(compareBy<SearchItem> { !it.word.equals(query, ignoreCase = true) }
            .thenBy { it.word.lowercase() }
            .thenBy { it.meaning.id })
    return ordered.groupBy { it.meaning.partOfSpeech }.map { (part, items) -> SearchGroup(part, items) }
}

fun searchSuggestions(query: String, words: List<Word>): List<String> = words.asSequence()
    .map { it.text }
    .distinctBy { it.lowercase() }
    .sortedWith(compareBy<String> { !it.equals(query, ignoreCase = true) }.thenBy { it.lowercase() })
    .take(5)
    .toList()
