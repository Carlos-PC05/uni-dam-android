package com.example.aplicacion.ui.inicio

import com.example.aplicacion.data.cash.InitialBalanceRepository
import com.example.aplicacion.model.InitialBalance
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InitialBalanceViewModelTest {
    @Before fun setMain() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun resetMain() { Dispatchers.resetMain() }

    @Test fun confirmsExactPositiveNegativeAndZeroAmounts() = runTest {
        for ((input, cents) in listOf("12,30" to 1230L, "-0.29" to -29L, "0" to 0L)) {
            val viewModel = InitialBalanceViewModel(FakeRepository())
            viewModel.load("alice")
            runCurrent()
            viewModel.updateAmount(input)
            viewModel.updateDate("29/02/2024")
            viewModel.save("alice")
            runCurrent()
            assertEquals(InitialBalance(cents, LocalDate.of(2024, 2, 29)), viewModel.state.value.balance)
        }
    }

    @Test fun rejectsInvalidAmountsAndCalendarDates() = runTest {
        val viewModel = InitialBalanceViewModel(FakeRepository())
        viewModel.load("alice")
        runCurrent()
        for (amount in listOf("1,234", "1e2", "92233720368547758,08", "")) {
            viewModel.updateAmount(amount)
            viewModel.updateDate("01/01/2024")
            viewModel.save("alice")
            assertNotNull(viewModel.state.value.amountError)
        }
        val tomorrow = LocalDate.now().plusDays(1).format(calendarDateFormat)
        for (date in listOf("30/02/2024", tomorrow)) {
            viewModel.updateAmount("12")
            viewModel.updateDate(date)
            viewModel.save("alice")
            assertNotNull(viewModel.state.value.dateError)
        }
        runCurrent()
        assertNull(viewModel.state.value.balance)
    }

    @Test fun retainsFormUntilSaveIsConfirmedAndRestoresBalanceOnReopen() = runTest {
        val remote = FakeRepository()
        val viewModel = InitialBalanceViewModel(remote)
        viewModel.load("alice")
        runCurrent()
        viewModel.updateAmount("-1,25")
        viewModel.updateDate("01/02/2024")
        remote.confirmation = CompletableDeferred()
        viewModel.save("alice")
        viewModel.save("alice")
        runCurrent()
        assertTrue(viewModel.state.value.busy)
        assertNull(viewModel.state.value.balance)
        remote.confirmation.completeExceptionally(IllegalStateException())
        runCurrent()
        assertNotNull(viewModel.state.value.message)
        assertFalse(viewModel.state.value.busy)
        assertEquals("-1,25", viewModel.state.value.amount)
        assertEquals("01/02/2024", viewModel.state.value.date)

        remote.confirmation = CompletableDeferred(Unit)
        viewModel.save("alice")
        runCurrent()
        val balance = InitialBalance(-125, LocalDate.of(2024, 2, 1))
        assertEquals(balance, viewModel.state.value.balance)

        val reopened = InitialBalanceViewModel(remote)
        reopened.load("alice")
        runCurrent()
        assertEquals(balance, reopened.state.value.balance)
    }

    @Test fun failedReadDoesNotOfferCreationAndCanRetry() = runTest {
        val remote = FakeRepository().apply { failRead = true }
        val viewModel = InitialBalanceViewModel(remote)
        viewModel.load("alice")
        runCurrent()
        assertFalse(viewModel.state.value.loaded)
        assertNotNull(viewModel.state.value.message)
        remote.failRead = false
        viewModel.load("alice")
        runCurrent()
        assertTrue(viewModel.state.value.loaded)
        assertNull(viewModel.state.value.message)
    }

    private class FakeRepository : InitialBalanceRepository {
        private var saved: InitialBalance? = null
        var failRead = false
        var confirmation = CompletableDeferred(Unit)

        override suspend fun load(uid: String): InitialBalance? {
            check(!failRead)
            return saved
        }

        override suspend fun create(uid: String, balance: InitialBalance): InitialBalance {
            confirmation.await()
            return balance.also { saved = it }
        }
    }
}
