package com.example.aplicacion.ui.movimientos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.aplicacion.model.InitialBalance
import com.example.aplicacion.model.MovementCategory
import com.example.aplicacion.model.cashFlow
import com.example.aplicacion.ui.inicio.calendarDateFormat
import java.math.BigDecimal
import java.math.BigInteger
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CashSummary(
    balance: InitialBalance,
    state: MovementsState,
    onMonth: (YearMonth) -> Unit,
    onRetry: () -> Unit,
) {
    CashStatus(state, onRetry)
    state.movements?.let { movements ->
        Text("Efectivo", style = MaterialTheme.typography.titleLarge)
        Text(euros(BigInteger.valueOf(balance.cents) + movements.cashFlow().net),
            style = MaterialTheme.typography.headlineMedium)
        MonthSelector(state.month, balance, onMonth)
        val flow = movements.cashFlow(state.month)
        Text("Ingresos: ${euros(flow.income)}")
        Text("Gastos: ${euros(flow.expenses)}")
        Text("Flujo de caja: ${if (flow.net.signum() > 0) "+" else ""}${euros(flow.net)}")
    }
}

@Composable
fun MovementsScreen(state: MovementsState, viewModel: MovementsViewModel, uid: String, onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Movimientos", style = MaterialTheme.typography.headlineMedium)
                CashStatus(state) { viewModel.load(uid) }
            }
            val balance = state.balance
            if (balance != null) {
                item {
                    Button(onClick = onAdd, enabled = !state.busy && state.movements != null) {
                        Text(if (state.awaitingConfirmation) "Continuar guardado" else "Registrar movimiento")
                    }
                    MonthSelector(state.month, balance, viewModel::selectMonth)
                }
                val visible = state.movements.orEmpty()
                    .filter { YearMonth.from(it.date) == state.month }
                    .sortedByDescending { it.date }
                if (state.movements != null && visible.isEmpty()) item { Text("No hay movimientos en este mes.") }
                items(visible, key = { it.id }) { movement ->
                    val income = movement.category == MovementCategory.INCOME
                    Text("${movement.date.format(calendarDateFormat)} · ${movement.category.label}")
                    Text(
                        "${if (income) "+" else "−"}${euros(BigInteger.valueOf(movement.cents))}",
                        color = if (income) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                    if (movement.note.isNotEmpty()) Text(movement.note)
                }
            } else if (state.movements != null) {
                item { Text("Configura el saldo inicial en Inicio para registrar movimientos.") }
            }
        }
    }
}

@Composable
fun MovementFormScreen(state: MovementsState, viewModel: MovementsViewModel, uid: String, onSaved: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MovementFields(state.form, !state.busy && !state.awaitingConfirmation, viewModel::edit)
        Button(onClick = { viewModel.save(uid, onSaved) }, enabled = !state.busy) {
            Text(when {
                state.busy -> "Guardando…"
                state.awaitingConfirmation -> "Reintentar guardado"
                else -> "Guardar movimiento"
            })
        }
        state.error?.let { CashError(it) }
    }
}

@Composable
private fun MovementFields(form: MovementForm, enabled: Boolean, onEdit: (MovementForm) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Registrar movimiento", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(form.amount, { onEdit(form.copy(amount = it)) }, Modifier.fillMaxWidth(),
            label = { Text("Importe (€)") }, enabled = enabled, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OutlinedTextField(form.date, { onEdit(form.copy(date = it)) }, Modifier.fillMaxWidth(),
            label = { Text("Fecha (DD/MM/AAAA)") }, enabled = enabled, singleLine = true)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
                Text("Categoría: ${form.category.label}")
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                MovementCategory.entries.forEach { category ->
                    DropdownMenuItem(text = { Text(category.label) }, onClick = {
                        onEdit(form.copy(category = category))
                        expanded = false
                    })
                }
            }
        }
        OutlinedTextField(form.note, { onEdit(form.copy(note = it)) }, Modifier.fillMaxWidth(),
            label = { Text("Nota (opcional)") }, enabled = enabled)
    }
}

@Composable
private fun MonthSelector(month: YearMonth, balance: InitialBalance, onMonth: (YearMonth) -> Unit) {
    Text(month.format(DateTimeFormatter.ofPattern("MMMM uuuu", Locale.forLanguageTag("es-ES"))),
        style = MaterialTheme.typography.titleMedium)
    Column {
        TextButton(onClick = { onMonth(month.minusMonths(1)) }, enabled = month > YearMonth.from(balance.date)) {
            Text("Mes anterior")
        }
        TextButton(onClick = { onMonth(month.plusMonths(1)) }, enabled = month < YearMonth.now()) {
            Text("Mes siguiente")
        }
    }
}

@Composable
internal fun CashStatus(state: MovementsState, onRetry: () -> Unit) {
    if (state.busy || (state.movements == null && state.loadError == null)) {
        CircularProgressIndicator()
        Text(if (state.busy && state.movements != null) "Actualizando…" else "Consultando movimientos…")
    }
    state.loadError?.let {
        CashError(it)
        Button(onClick = onRetry, enabled = !state.busy) { Text("Reintentar") }
    }
}

@Composable
private fun CashError(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

private fun euros(cents: BigInteger): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-ES")).format(BigDecimal(cents, 2))
