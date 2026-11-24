package com.example.aplicacion.ui.movimientos

import com.example.aplicacion.data.cash.InitialBalanceRepository
import com.example.aplicacion.data.cash.MovementRejected
import com.example.aplicacion.data.cash.MovementRepository
import com.example.aplicacion.data.cash.PendingMovementStore
import com.example.aplicacion.model.InitialBalance
import com.example.aplicacion.model.Movement
import com.example.aplicacion.model.MovementCategory
import com.example.aplicacion.model.cashFlow
import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MovementsViewModelTest {
    @Before fun setMain() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun resetMain() { Dispatchers.resetMain() }

    @Test fun recoversTheSameAttemptAfterLosingTheRemoteConfirmation() = runTest {
        val local = FakeStore()
        val remote = FakeRepository().apply { loseResponse = true }
        val first = MovementsViewModel(remote, Balances, local)
        first.load("alice")
        advanceUntilIdle()
        first.edit(first.state.value.form.copy(amount = "12,50", note = "Prueba"))
        first.save("alice") { fail("La respuesta no ha llegado") }
        advanceUntilIdle()

        val pending = checkNotNull(local.load("alice"))
        assertTrue(first.state.value.awaitingConfirmation)
        assertEquals(1, remote.movements.size)

        val reopened = MovementsViewModel(remote, Balances, local)
        reopened.load("alice")
        advanceUntilIdle()
        assertTrue(reopened.state.value.awaitingConfirmation)
        assertEquals("12.50", reopened.state.value.form.amount)
        assertEquals("Prueba", reopened.state.value.form.note)

        remote.loseResponse = false
        var saved = false
        reopened.save("alice") { saved = true }
        advanceUntilIdle()
        assertTrue(saved)
        assertEquals(listOf(pending.id, pending.id), remote.attempts)
        assertEquals(listOf(pending), reopened.state.value.movements)
        assertNull(local.load("alice"))
    }

    @Test fun preservesEditableFieldsAfterAConfirmedRejection() = runTest {
        val local = FakeStore()
        val remote = FakeRepository().apply { reject = true }
        val viewModel = MovementsViewModel(remote, Balances, local)
        viewModel.load("alice")
        advanceUntilIdle()
        val form = viewModel.state.value.form.copy(amount = "5")
        viewModel.edit(form)
        viewModel.save("alice") { fail("Firestore ha rechazado el movimiento") }
        advanceUntilIdle()

        assertFalse(viewModel.state.value.awaitingConfirmation)
        assertEquals(form, viewModel.state.value.form)
        assertNotNull(viewModel.state.value.error)
        assertNull(local.load("alice"))

        remote.reject = false
        viewModel.edit(form.copy(amount = "6"))
        viewModel.save("alice") {}
        advanceUntilIdle()
        assertNotEquals(remote.attempts.first(), remote.attempts.last())
        assertEquals(600L, remote.movements.values.single().cents)
    }

    @Test fun editingAcrossMonthsAndCategoriesReplacesTheConfirmedMovement() = runTest {
        val income = Movement("income", 50_000, LocalDate.of(2024, 1, 1), MovementCategory.INCOME, "Nómina")
        val expense = Movement("expense", 20_000, LocalDate.of(2024, 1, 2), MovementCategory.FOOD, "")
        val remote = FakeRepository().apply {
            movements[income.id] = income
            movements[expense.id] = expense
        }
        val vm = MovementsViewModel(remote, Balances, FakeStore())
        vm.load("alice")
        advanceUntilIdle()
        val january = YearMonth.of(2024, 1)
        val february = january.plusMonths(1)
        vm.selectMonth(january)
        vm.selectCategory(MovementCategory.INCOME)
        assertEquals(listOf(income), vm.state.value.visibleMovements)
        vm.openCorrection(income.id)
        val initial = checkNotNull(vm.state.value.correction).form
        assertEquals(MovementForm("500.00", "01/01/2024", MovementCategory.INCOME, "Nómina"), initial)
        val corrected = MovementForm("100,50", "01/02/2024", MovementCategory.HOUSING, "")
        for (invalid in listOf(corrected.copy(amount = "0"), corrected.copy(amount = "12,345"),
            corrected.copy(date = "31/12/2023"), corrected.copy(date = "01/01/9999"))) {
            vm.changeCorrection(invalid)
            vm.saveCorrection("alice")
            advanceUntilIdle()
            assertNotNull(vm.state.value.correction?.error)
            assertEquals(income, remote.movements[income.id])
        }
        vm.changeCorrection(corrected)
        remote.loseResponse = true
        vm.saveCorrection("alice")
        advanceUntilIdle()
        assertEquals(corrected, vm.state.value.correction?.form)
        assertNotNull(vm.state.value.correction?.error)
        assertTrue(vm.state.value.correction?.action is CorrectionAction.Saving)
        vm.changeCorrection(corrected.copy(amount = "25"))
        assertEquals(corrected, vm.state.value.correction?.form)
        assertEquals(listOf(income), vm.state.value.visibleMovements)

        remote.loseResponse = false
        vm.saveCorrection("alice")
        advanceUntilIdle()
        assertEquals(CorrectionAction.Completed, vm.state.value.correction?.action)
        assertTrue(vm.state.value.visibleMovements.isEmpty())
        val confirmed = checkNotNull(vm.state.value.movements)
        assertEquals(2, confirmed.size)
        assertEquals(BigInteger.valueOf(-20_000), confirmed.cashFlow(january).net)
        assertEquals(BigInteger.valueOf(-10_050), confirmed.cashFlow(february).net)
        assertEquals(BigInteger.valueOf(-30_050), confirmed.cashFlow().net)
        vm.selectMonth(february)
        assertTrue(vm.state.value.visibleMovements.isEmpty())
        vm.selectCategory(MovementCategory.HOUSING)
        assertEquals(income.id, vm.state.value.visibleMovements.single().id)

        vm.openCorrection(expense.id)
        val rejected = MovementForm("25", "01/01/2024", MovementCategory.FOOD, "")
        vm.changeCorrection(rejected)
        remote.reject = true
        vm.saveCorrection("alice")
        advanceUntilIdle()
        assertEquals(rejected, vm.state.value.correction?.form)
        assertEquals(CorrectionAction.Editing, vm.state.value.correction?.action)
        assertNotNull(vm.state.value.correction?.error)
        remote.reject = false
        vm.changeCorrection(rejected.copy(amount = "26"))
        vm.saveCorrection("alice")
        advanceUntilIdle()
        assertEquals(BigInteger.valueOf(-2_600), checkNotNull(vm.state.value.movements).cashFlow(january).net)
    }

    @Test fun deletionCanBeCancelledAndRetriedAfterLosingTheConfirmation() = runTest {
        val date = LocalDate.of(2024, 1, 1)
        val income = Movement("income", 5_000, date, MovementCategory.INCOME, "")
        val expense = Movement("expense", 2_000, date, MovementCategory.FOOD, "")
        val remote = FakeRepository().apply {
            movements[income.id] = income
            movements[expense.id] = expense
        }
        val vm = MovementsViewModel(remote, Balances, FakeStore())
        vm.load("alice")
        advanceUntilIdle()
        vm.openCorrection(expense.id)
        vm.requestDeletion(true)
        vm.requestDeletion(false)
        vm.confirmDeletion("alice")
        advanceUntilIdle()
        assertEquals(BigInteger.valueOf(3_000), checkNotNull(vm.state.value.movements).cashFlow().net)

        vm.requestDeletion(true)
        remote.loseResponse = true
        vm.confirmDeletion("alice")
        advanceUntilIdle()
        assertNotNull(vm.state.value.correction?.error)
        vm.requestDeletion(false)
        assertEquals(CorrectionAction.Deleting, vm.state.value.correction?.action)
        assertEquals(listOf(income, expense), vm.state.value.movements)

        remote.loseResponse = false
        vm.confirmDeletion("alice")
        advanceUntilIdle()
        assertEquals(listOf(income), vm.state.value.movements)
        vm.openCorrection(income.id)
        vm.requestDeletion(true)
        vm.confirmDeletion("alice")
        advanceUntilIdle()
        assertEquals(emptyList<Movement>(), vm.state.value.movements)
    }

    private class FakeStore : PendingMovementStore {
        private val pending = mutableMapOf<String, Movement>()
        override suspend fun load(uid: String): Movement? = pending[uid]
        override suspend fun save(uid: String, movement: Movement) { pending[uid] = movement }
        override suspend fun clear(uid: String) { pending.remove(uid) }
    }

    private class FakeRepository : MovementRepository {
        val attempts = mutableListOf<String>()
        val movements = mutableMapOf<String, Movement>()
        var loseResponse = false
        var reject = false

        override suspend fun load(uid: String): List<Movement> = movements.values.toList()

        override suspend fun create(uid: String, movement: Movement) {
            attempts += movement.id
            if (reject) throw MovementRejected()
            movements.putIfAbsent(movement.id, movement)
            if (loseResponse) awaitCancellation()
        }

        override suspend fun update(uid: String, original: Movement, replacement: Movement) {
            check(movements[original.id] in listOf(original, replacement))
            if (reject) throw MovementRejected()
            movements[original.id] = replacement
            if (loseResponse) awaitCancellation()
        }

        override suspend fun delete(uid: String, movement: Movement) {
            movements.remove(movement.id)
            if (loseResponse) awaitCancellation()
        }
    }

    private object Balances : InitialBalanceRepository {
        override suspend fun load(uid: String) = InitialBalance(100_000, LocalDate.of(2024, 1, 1))
        override suspend fun create(uid: String, balance: InitialBalance) = error("El saldo ya existe")
    }
}
