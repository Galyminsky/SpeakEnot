package com.galyaminsky.speakenot

import com.galyaminsky.speakenot.data.DictionaryRepositoryImpl
import com.galyaminsky.speakenot.data.local.CacheMetadataEntity
import com.galyaminsky.speakenot.data.local.EnglishFavoriteEntity
import com.galyaminsky.speakenot.data.local.HistoryEntity
import com.galyaminsky.speakenot.data.local.KazakhFavoriteEntity
import com.galyaminsky.speakenot.data.remote.ExampleDto
import com.galyaminsky.speakenot.data.remote.MeaningDto
import com.galyaminsky.speakenot.data.remote.MeaningSummaryDto
import com.galyaminsky.speakenot.data.remote.RemoteDictionaryDataSource
import com.galyaminsky.speakenot.data.remote.TextDto
import com.galyaminsky.speakenot.data.remote.WordDto
import com.galyaminsky.speakenot.data.remote.toDomain
import com.galyaminsky.speakenot.domain.DictionaryError
import com.galyaminsky.speakenot.domain.DictionaryResult
import com.galyaminsky.speakenot.ui.AppRoute
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DictionaryFoundationTest {
    @Test fun wordMapperDropsInvalidIdentityAndKeepsOptionalFields() {
        assertNull(WordDto(null, "test").toDomain())
        assertNull(WordDto(1, " ").toDomain())
        val word = WordDto(1, " test ", listOf(MeaningSummaryDto(null), MeaningSummaryDto(2, TextDto(null), null))).toDomain()!!
        assertEquals("test", word.text)
        assertEquals(1, word.meanings.size)
        assertNull(word.meanings.single().translation)
        assertNull(word.meanings.single().transcription)
    }

    @Test fun meaningMapperHandlesMissingNestedFieldsAndExamples() {
        assertNull(MeaningDto().toDomain())
        val meaning = MeaningDto(4, " word ", examples = listOf(ExampleDto(null), ExampleDto(" "), ExampleDto(" example "))).toDomain()!!
        assertEquals("word", meaning.text)
        assertNull(meaning.definition)
        assertNull(meaning.translation)
        assertEquals(listOf("example"), meaning.examples)
    }

    @Test fun repositoryNormalizesInputsMapsValidItemsAndPropagatesError() = runBlocking {
        val source = FakeSource()
        val repository = DictionaryRepositoryImpl(source)
        source.words = DictionaryResult.Success(listOf(WordDto(1, " go "), WordDto(null, "bad")))
        assertEquals(listOf("go"), (repository.search(" go ") as DictionaryResult.Success).value.map { it.text })
        assertEquals("go", source.query)
        source.details = DictionaryResult.Success(listOf(MeaningDto(2, "go"), MeaningDto()))
        assertEquals(1, (repository.meanings(listOf(-1, 2, 2)) as DictionaryResult.Success).value.size)
        assertEquals(listOf(2L), source.ids)
        source.words = DictionaryResult.Failure(DictionaryError.Http(503))
        assertEquals(DictionaryResult.Failure(DictionaryError.Http(503)), repository.search("x"))
    }

    @Test fun localModelsSeparateLanguagesAndKeepOnlyAppOwnedFields() {
        assertNotEquals(EnglishFavoriteEntity::class, KazakhFavoriteEntity::class)
        assertEquals(1L, EnglishFavoriteEntity(1, "Skyeng", 1).meaningId)
        assertEquals(2L, KazakhFavoriteEntity(2, "Skyeng", 2).meaningId)
        assertEquals("hello", HistoryEntity("ENG", "hello", "hello", 3).query)
        assertEquals("check", CacheMetadataEntity("check", 4).key)
        assertFalse(CacheMetadataEntity::class.java.declaredFields.any { it.name == "payload" || it.name == "response" })
    }

    @Test fun wordCardHidesBottomNavigation() {
        assertFalse(AppRoute.showBottomBar(AppRoute.Word))
        assertTrue(AppRoute.showBottomBar(AppRoute.Home))
        assertTrue(AppRoute.showBottomBar(AppRoute.Search))
        assertTrue(AppRoute.showBottomBar(AppRoute.Favorites))
    }

    private class FakeSource : RemoteDictionaryDataSource {
        var query = ""
        var ids = emptyList<Long>()
        var words: DictionaryResult<List<WordDto>> = DictionaryResult.Success(emptyList())
        var details: DictionaryResult<List<MeaningDto>> = DictionaryResult.Success(emptyList())
        override suspend fun search(query: String): DictionaryResult<List<WordDto>> { this.query = query; return words }
        override suspend fun meanings(ids: List<Long>): DictionaryResult<List<MeaningDto>> { this.ids = ids; return details }
    }
}
