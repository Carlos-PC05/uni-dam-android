package com.example.aplicacion.ui.movimientos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aplicacion.data.cash.InitialBalanceRepository
import com.example.aplicacion.data.cash.MovementChanged
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

data class MovementCorrection(
    val original: Movement,
    val form: MovementForm,
    val action: CorrectionAction = CorrectionAction.Editing,
    val error: String? = null,
)

sealed interface CorrectionAction {
    data object Editing : CorrectionAction
    data class Saving(val replacement: Movement) : CorrectionAction
    data object ConfirmDeletion : CorrectionAction
    data object Deleting : CorrectionAction
    data object Completed : CorrectionAction
}

data class MovementsState(
    val balance: InitialBalance? = null,
    val movements: List<Movement>? = null,
    val month: YearMonth = YearMonth.now(),
    val category: MovementCategory? = null,
    val form: MovementForm = MovementForm(),
    val correction: MovementCorrection? = null,
    val busy: Boolean = false,
    val awaitingConfirmation: Boolean = false,
    val error: String? = null,
    val loadError: String? = null,
) {
    val visibleMovements: List<Movement>
        get() = movements.orEmpty().filter {
            YearMonth.from(it.date) == month && (category == null || it.category == category)
        }.sortedByDescending { it.date }
}

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
                    form = restored.toForm(),
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

    fun selectCategory(category: MovementCategory?) = _state.update { it.copy(category = category) }

    fun edit(form: MovementForm) = _state.update {
        if (it.busy || it.awaitingConfirmation) it else it.copy(form = form, error = null)
    }

    fun save(uid: String, onSaved: () -> Unit) {
        val current = _state.value
        val balance = current.balance ?: return
        if (current.busy) return
        val movement = pending ?: validatedMovement(current.form, balance, UUID.randomUUID().toString()) {
            _state.update { state -> state.copy(error = it) }
        } ?: return
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

    fun openCorrection(id: String) = _state.update { state ->
        val movement = state.movements?.find { it.id == id }
        when {
            state.busy -> state
            state.correction?.original?.id == id && state.correction.action != CorrectionAction.Completed -> state
            movement == null -> state.copy(correction = null)
            else -> state.copy(correction = MovementCorrection(movement, movement.toForm()))
        }
    }

    fun closeCorrection() = _state.update { if (it.busy) it else it.copy(correction = null) }

    fun changeCorrection(form: MovementForm) = changeCorrectionState {
        if (it.action == CorrectionAction.Editing) it.copy(form = form, error = null) else it
    }

    fun requestDeletion(confirming: Boolean) = changeCorrectionState {
        when {
            confirming && it.action == CorrectionAction.Editing ->
                it.copy(action = CorrectionAction.ConfirmDeletion, error = null)
            !confirming && it.action == CorrectionAction.ConfirmDeletion ->
                it.copy(action = CorrectionAction.Editing, error = null)
            else -> it
        }
    }

    fun saveCorrection(uid: String) {
        val state = _state.value
        val correction = state.correction ?: return
        val balance = state.balance ?: return
        if (state.busy) return
        val replacement = when (val action = correction.action) {
            is CorrectionAction.Saving -> action.replacement
            CorrectionAction.Editing -> {
                val validated = validatedMovement(correction.form, balance, correction.original.id, ::correctionError)
                    ?.copy(revision = correction.original.revision) ?: return
                if (validated == correction.original) validated else validated.copy(revision = UUID.randomUUID().toString())
            }
            else -> return
        }
        changeCorrectionState { it.copy(action = CorrectionAction.Saving(replacement)) }
        perform("No se han podido confirmar los cambios. Puedes reintentar.", correcting = true) {
            withTimeout(30_000) { repository.update(uid, correction.original, replacement) }
            _state.update { current ->
                current.copy(
                    correction = correction.copy(action = CorrectionAction.Completed, error = null),
                    movements = current.movements.orEmpty().map { if (it.id == replacement.id) replacement else it },
                )
            }
        }
    }

    fun confirmDeletion(uid: String) {
        val state = _state.value
        val correction = state.correction ?: return
        if (state.busy || correction.action !in listOf(CorrectionAction.ConfirmDeletion, CorrectionAction.Deleting)) return
        changeCorrectionState { it.copy(action = CorrectionAction.Deleting) }
        perform("No se ha podido confirmar la eliminación. Puedes reintentar.", correcting = true) {
            withTimeout(30_000) { repository.delete(uid, correction.original) }
            _state.update { current ->
                current.copy(
                    correction = correction.copy(action = CorrectionAction.Completed, error = null),
                    movements = current.movements.orEmpty().filterNot { it.id == correction.original.id },
                )
            }
        }
    }

    private fun changeCorrectionState(change: (MovementCorrection) -> MovementCorrection) = _state.update {
        if (it.busy) it else it.copy(correction = it.correction?.let(change))
    }

    private fun correctionError(message: String?) = _state.update {
        it.copy(correction = it.correction?.copy(error = message))
    }

    private fun rejectCorrection(message: String) = _state.update {
        it.copy(correction = it.correction?.copy(action = CorrectionAction.Editing, error = message))
    }

    private fun Movement.toForm() = MovementForm(
        BigDecimal.valueOf(cents, 2).toPlainString(), date.format(calendarDateFormat), category, note,
    )

    private fun validatedMovement(
        form: MovementForm,
        balance: InitialBalance,
        id: String,
        onError: (String) -> Unit,
    ): Movement? {
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
            onError(error)
            return null
        }
        return Movement(id, checkNotNull(cents), checkNotNull(date), form.category, form.note.trim())
    }

    private fun perform(
        failure: String,
        loading: Boolean = false,
        correcting: Boolean = false,
        action: suspend () -> Unit,
    ) {
        if (_state.value.busy) return
        fun report(message: String?) {
            when {
                loading -> _state.update { it.copy(loadError = message) }
                correcting -> correctionError(message)
                else -> _state.update { it.copy(error = message) }
            }
        }
        _state.update { it.copy(busy = true) }
        report(null)
        viewModelScope.launch {
            try {
                action()
            } catch (_: MovementRejected) {
                rejectCorrection("Firestore rechazó los cambios. Revisa los datos antes de guardar.")
            } catch (_: MovementChanged) {
                rejectCorrection("El movimiento ha cambiado en Firestore. Vuelve a Movimientos para consultarlo.")
            } catch (_: TimeoutCancellationException) {
                report(failure)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                report(failure)
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
