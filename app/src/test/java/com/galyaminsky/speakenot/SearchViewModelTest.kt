package com.galyaminsky.speakenot

import com.galyaminsky.speakenot.domain.DictionaryError
import com.galyaminsky.speakenot.domain.DictionaryRepository
import com.galyaminsky.speakenot.domain.DictionaryResult
import com.galyaminsky.speakenot.domain.Meaning
import com.galyaminsky.speakenot.domain.MeaningSummary
import com.galyaminsky.speakenot.domain.Word
import com.galyaminsky.speakenot.ui.DictionaryViewModel
import com.galyaminsky.speakenot.ui.SearchStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.awaitCancellation
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun debounceWaits400msAndUsesLatestNormalizedPhrase() = runTest {
        val fake = FakeRepository()
        val vm = DictionaryViewModel(fake)
        vm.onQueryChanged("good")
        advanceTimeBy(200)
        vm.onQueryChanged("  good   morning ")
        advanceTimeBy(399)
        runCurrent()
        assertTrue(fake.queries.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("good morning"), fake.queries)
        assertEquals(SearchStatus.Empty, vm.state.value.status)
    }

    @Test fun keyboardSearchStartsImmediatelyAndCancelsDebounce() = runTest {
        val fake = FakeRepository()
        val vm = DictionaryViewModel(fake)
        vm.onQueryChanged("book")
        vm.searchNow()
        runCurrent()
        assertEquals(listOf("book"), fake.queries)
        advanceTimeBy(400)
        runCurrent()
        assertEquals(1, fake.queries.size)
    }

    @Test fun cancellationAndLateNonCooperativeResponseCannotOverwriteNewQuery() = runTest {
        val old = CompletableDeferred<DictionaryResult<List<Word>>>()
        val fake = FakeRepository()
        fake.handler = { query ->
            if (query == "old") withContext(NonCancellable) { old.await() }
            else DictionaryResult.Success(listOf(Word(2, "new", listOf(MeaningSummary(3, "новый", null)))))
        }
        val vm = DictionaryViewModel(fake)
        vm.onQueryChanged("old")
        advanceTimeBy(400)
        runCurrent()
        vm.onQueryChanged("new")
        advanceTimeBy(400)
        runCurrent()
        assertEquals("new", vm.state.value.groups.single().items.single().word)
        old.complete(DictionaryResult.Success(listOf(Word(1, "old", listOf(MeaningSummary(1, "старый", null))))))
        advanceUntilIdle()
        assertEquals("new", vm.state.value.groups.single().items.single().word)
    }

    @Test fun changingQueryCancelsActiveRepositoryCall() = runTest {
        val fake = FakeRepository()
        var cancelled = false
        fake.handler = { query ->
            if (query == "first") {
                try { awaitCancellation() } finally { cancelled = true }
            }
            DictionaryResult.Success(emptyList())
        }
        val vm = DictionaryViewModel(fake)
        vm.onQueryChanged("first")
        advanceTimeBy(400)
        runCurrent()
        vm.onQueryChanged("second")
        runCurrent()
        assertTrue(cancelled)
        advanceUntilIdle()
        assertEquals(listOf("first", "second"), fake.queries)
    }

    @Test fun emptyAndInvalidInputCancelWorkAndClearOldResults() = runTest {
        val fake = FakeRepository()
        fake.handler = { DictionaryResult.Success(listOf(Word(1, "go", listOf(MeaningSummary(2, "идти", null))))) }
        val vm = DictionaryViewModel(fake)
        vm.onQueryChanged("go")
        advanceUntilIdle()
        assertEquals(SearchStatus.Success, vm.state.value.status)
        vm.onQueryChanged("go идти")
        assertEquals(SearchStatus.Invalid, vm.state.value.status)
        assertTrue(vm.state.value.groups.isEmpty())
        vm.onQueryChanged("   ")
        assertEquals(SearchStatus.Idle, vm.state.value.status)
        advanceUntilIdle()
        assertEquals(1, fake.queries.size)
    }

    @Test fun httpAndTimeoutErrorsCanBeRetried() = runTest {
        val fake = FakeRepository()
        var calls = 0
        fake.handler = {
            calls++
            when (calls) {
                1 -> DictionaryResult.Failure(DictionaryError.Http(503))
                2 -> DictionaryResult.Failure(DictionaryError.Timeout)
                else -> DictionaryResult.Success(listOf(Word(1, "go", listOf(MeaningSummary(2, "идти", null)))))
            }
        }
        val vm = DictionaryViewModel(fake)
        vm.onQueryChanged("go")
        advanceUntilIdle()
        assertEquals(SearchStatus.Error(DictionaryError.Http(503)), vm.state.value.status)
        vm.retry()
        runCurrent()
        assertEquals(SearchStatus.Error(DictionaryError.Timeout), vm.state.value.status)
        vm.retry()
        runCurrent()
        assertEquals(SearchStatus.Success, vm.state.value.status)
        assertEquals(3, calls)
    }

    @Test fun wordsWithoutMeaningsStillProvideSuggestions() = runTest {
        val fake = FakeRepository()
        fake.handler = { DictionaryResult.Success(listOf(Word(1, "book", emptyList()))) }
        val vm = DictionaryViewModel(fake)
        vm.onQueryChanged("boo")
        advanceUntilIdle()
        assertEquals(SearchStatus.Success, vm.state.value.status)
        assertEquals(listOf("book"), vm.state.value.suggestions)
        assertTrue(vm.state.value.groups.isEmpty())
    }

    private class FakeRepository : DictionaryRepository {
        val queries = mutableListOf<String>()
        var handler: suspend (String) -> DictionaryResult<List<Word>> = { DictionaryResult.Success(emptyList()) }
        override suspend fun search(query: String): DictionaryResult<List<Word>> {
            queries += query
            return handler(query)
        }
        override suspend fun meanings(ids: List<Long>): DictionaryResult<List<Meaning>> = error("Not used by search")
    }
}
