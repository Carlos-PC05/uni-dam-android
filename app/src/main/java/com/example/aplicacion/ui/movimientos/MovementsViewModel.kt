package com.example.aplicacion.ui.movimientos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aplicacion.data.cash.InitialBalanceRepository
import com.example.aplicacion.data.cash.MovementRejected
import com.example.aplicacion.data.cash.MovementRepository
import com.example.aplicacion.data.cash.PendingMovementStore
import com.example.aplicacion.model.InitialBalance
import com.example.aplicacion.model.Movement
import com.example.aplicacion.model.MovementCategory
import com.example.aplicacion.ui.inicio.calendarDateFormat
import com.example.aplicacion.ui.inicio.parseCents
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class MovementForm(
    val amount: String = "",
    val date: String = LocalDate.now().format(calendarDateFormat),
    val category: MovementCategory = MovementCategory.INCOME,
    val note: String = "",
)

data class MovementsState(
    val balance: InitialBalance? = null,
    val movements: List<Movement>? = null,
    val month: YearMonth = YearMonth.now(),
    val form: MovementForm = MovementForm(),
    val busy: Boolean = false,
    val awaitingConfirmation: Boolean = false,
    val error: String? = null,
    val loadError: String? = null,
)

@HiltViewModel
class MovementsViewModel @Inject constructor(
    private val repository: MovementRepository,
    private val balances: InitialBalanceRepository,
    private val pendingStore: PendingMovementStore,
) : ViewModel() {
    private val _state = MutableStateFlow(MovementsState())
    val state = _state.asStateFlow()
    private var pending: Movement? = null

    fun load(uid: String) = perform("No se han podido consultar los movimientos.", loading = true) {
        val restored = pendingStore.load(uid)
        if (restored != null && pending != restored) {
            pending = restored
            _state.update {
                it.copy(
                    form = MovementForm(
                        BigDecimal.valueOf(restored.cents, 2).toPlainString(),
                        restored.date.format(calendarDateFormat),
                        restored.category,
                        restored.note,
                    ),
                    awaitingConfirmation = true,
                    error = "Hay un guardado pendiente. Reintenta para confirmar el movimiento.",
                )
            }
        }
        val balance = withTimeout(30_000) { balances.load(uid) }
        _state.update { it.copy(balance = balance) }
        val movements = if (balance == null) emptyList() else withTimeout(30_000) { repository.load(uid) }
        _state.update { it.copy(movements = movements) }
    }

    fun selectMonth(month: YearMonth) = _state.update { it.copy(month = month) }

    fun edit(form: MovementForm) = _state.update {
        if (it.busy || it.awaitingConfirmation) it else it.copy(form = form, error = null)
    }

    fun save(uid: String, onSaved: () -> Unit) {
        val current = _state.value
        val balance = current.balance ?: return
        if (current.busy) return
        val movement = pending ?: validatedMovement(current.form, balance) ?: return
        pending = movement
        _state.update { it.copy(awaitingConfirmation = true) }
        perform("No se ha podido confirmar el movimiento. Puedes reintentar.") {
            pendingStore.save(uid, movement)
            try {
                withTimeout(30_000) { repository.create(uid, movement) }
            } catch (_: MovementRejected) {
                pendingStore.clear(uid)
                pending = null
                _state.update {
                    it.copy(
                        awaitingConfirmation = false,
                        error = "Firestore rechazó el movimiento. Revisa los datos antes de volver a guardar.",
                    )
                }
                return@perform
            }

            pendingStore.clear(uid)
            pending = null
            _state.update {
                it.copy(
                    awaitingConfirmation = false,
                    movements = it.movements.orEmpty().filterNot { saved -> saved.id == movement.id } + movement,
                    month = YearMonth.from(movement.date),
                    form = MovementForm(),
                )
            }
            onSaved()
        }
    }

    private fun validatedMovement(form: MovementForm, balance: InitialBalance): Movement? {
        val cents = parseCents(form.amount.trim())
        val date = runCatching { LocalDate.parse(form.date.trim(), calendarDateFormat) }.getOrNull()
        val error = when {
            cents == null || cents <= 0 -> "Introduce un importe positivo con hasta dos decimales."
            date == null || date !in balance.date..LocalDate.now() ->
                "Introduce una fecha entre el saldo inicial y hoy."
            form.note.length > 500 -> "La nota admite hasta 500 caracteres."
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(error = error) }
            return null
        }
        return Movement(UUID.randomUUID().toString(), checkNotNull(cents), checkNotNull(date),
            form.category, form.note.trim())
    }

    private fun perform(failure: String, loading: Boolean = false, action: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { if (loading) it.copy(busy = true, loadError = null) else it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                action()
            } catch (_: TimeoutCancellationException) {
                _state.update { if (loading) it.copy(loadError = failure) else it.copy(error = failure) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { if (loading) it.copy(loadError = failure) else it.copy(error = failure) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
