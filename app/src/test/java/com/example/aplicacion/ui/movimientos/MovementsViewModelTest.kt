package com.example.aplicacion.ui.movimientos

import com.example.aplicacion.data.cash.InitialBalanceRepository
import com.example.aplicacion.data.cash.MovementRejected
import com.example.aplicacion.data.cash.MovementRepository
import com.example.aplicacion.data.cash.PendingMovementStore
import com.example.aplicacion.model.InitialBalance
import com.example.aplicacion.model.Movement
import java.time.LocalDate
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
    }

    private object Balances : InitialBalanceRepository {
        override suspend fun load(uid: String) = InitialBalance(100_000, LocalDate.of(2024, 1, 1))
        override suspend fun create(uid: String, balance: InitialBalance) = error("El saldo ya existe")
    }
}
