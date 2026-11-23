package com.example.aplicacion.ui.inicio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aplicacion.data.cash.InitialBalanceRepository
import com.example.aplicacion.model.InitialBalance
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

data class InitialBalanceUiState(
    val loaded: Boolean = false,
    val busy: Boolean = false,
    val balance: InitialBalance? = null,
    val amount: String = "",
    val date: String = "",
    val amountError: String? = null,
    val dateError: String? = null,
    val message: String? = null,
)

@HiltViewModel
class InitialBalanceViewModel @Inject constructor(
    private val repository: InitialBalanceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(InitialBalanceUiState())
    val state = _state.asStateFlow()

    /** Solo ofrece el alta después de confirmar que la referencia remota no existe. */
    fun load(uid: String) {
        if (_state.value.loaded || _state.value.busy) return
        perform("No se ha podido consultar el saldo. Reintenta.") {
            val balance = repository.load(uid)
            _state.update { it.copy(loaded = true, balance = balance ?: it.balance) }
        }
    }

    /** Incorpora el saldo inmutable que Movimientos confirmó para la misma cuenta. */
    fun acceptConfirmedBalance(balance: InitialBalance) = _state.update {
        it.copy(loaded = true, balance = balance, amount = "", date = "", message = null)
    }

    fun updateAmount(amount: String) {
        _state.update { it.copy(amount = amount, amountError = null) }
    }

    fun updateDate(date: String) {
        _state.update { it.copy(date = date, dateError = null) }
    }

    /** Conserva el formulario hasta que Firestore confirma la referencia. */
    fun save(uid: String) {
        val form = _state.value
        if (!form.loaded || form.busy || form.balance != null) return
        val cents = parseCents(form.amount.trim())
        val date = runCatching { LocalDate.parse(form.date.trim(), calendarDateFormat) }
            .getOrNull()?.takeIf { it.year in 1..9999 && it <= LocalDate.now() }
        if (cents == null || date == null) {
            _state.update { it.copy(
                amountError = if (cents == null) "Introduce un importe válido con hasta dos decimales." else null,
                dateError = if (date == null) "Introduce una fecha válida que no sea posterior a hoy." else null,
                message = null,
            ) }
            return
        }
        perform("No se ha podido confirmar el saldo. Puedes reintentar el guardado.") {
            val balance = repository.create(uid, InitialBalance(cents, date))
            _state.update { it.copy(balance = balance, amount = "", date = "") }
        }
    }

    private fun perform(failure: String, action: suspend () -> Unit) {
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                withTimeout(30_000) { action() }
            } catch (timeout: TimeoutCancellationException) {
                _state.update { it.copy(message = failure) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(message = failure) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}

internal val calendarDateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)

private val euroAmount = Regex("[+-]?[0-9]+([,.][0-9]{1,2})?")

internal fun parseCents(amount: String): Long? {
    if (!euroAmount.matches(amount)) return null
    return runCatching { amount.replace(',', '.').toBigDecimal().movePointRight(2).longValueExact() }.getOrNull()
}
