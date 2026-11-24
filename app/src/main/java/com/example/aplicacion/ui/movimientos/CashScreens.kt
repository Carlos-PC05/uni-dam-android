package com.example.aplicacion.ui.movimientos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
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
        val max = maxOf(flow.income, flow.expenses, BigInteger.ONE).toDouble()
        CashBar("Ingresos", flow.income, max, MaterialTheme.colorScheme.primary)
        CashBar("Gastos", flow.expenses, max, MaterialTheme.colorScheme.tertiary)
        Text("Flujo de caja: ${if (flow.net.signum() > 0) "+" else ""}${euros(flow.net)}")
    }
}

@Composable
private fun CashBar(label: String, cents: BigInteger, max: Double, color: Color) {
    Text("$label: ${euros(cents)}")
    Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth((cents.toDouble() / max).toFloat()).fillMaxHeight().background(color))
    }
}

@Composable
fun MovementsScreen(
    state: MovementsState,
    viewModel: MovementsViewModel,
    uid: String,
    onAdd: () -> Unit,
    onCorrect: (String) -> Unit,
) {
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
                    CategorySelector(state.category, allCategories = true, onSelect = viewModel::selectCategory)
                }
                val visible = state.visibleMovements
                if (state.movements != null && visible.isEmpty()) item { Text("No hay movimientos para estos filtros.") }
                items(visible, key = { it.id }) { movement ->
                    val income = movement.category == MovementCategory.INCOME
                    val date = movement.date.format(calendarDateFormat)
                    val amount = "${if (income) "+" else "−"}${euros(BigInteger.valueOf(movement.cents))}"
                    Text("$date · ${movement.category.label}")
                    Text(
                        amount,
                        color = if (income) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                    if (movement.note.isNotEmpty()) Text(movement.note)
                    TextButton(
                        onClick = { onCorrect(movement.id) },
                        modifier = Modifier.semantics {
                            contentDescription = "Editar movimiento del $date, ${movement.category.label}, $amount"
                        },
                        enabled = !state.busy && !state.awaitingConfirmation) {
                        Text("Editar movimiento")
                    }
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
        Text("Registrar movimiento", style = MaterialTheme.typography.titleLarge)
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
fun MovementCorrectionScreen(
    correction: MovementCorrection,
    busy: Boolean,
    onChange: (MovementForm) -> Unit,
    onSave: () -> Unit,
    onRequestDeletion: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler { if (!busy) onBack() }
    val action = correction.action
    val editing = action == CorrectionAction.Editing
    val saving = action is CorrectionAction.Saving
    val deleting = action == CorrectionAction.Deleting
    val showingConfirmation = action == CorrectionAction.ConfirmDeletion || deleting
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Editar movimiento", style = MaterialTheme.typography.titleLarge)
        MovementFields(correction.form, editing && !busy, onChange)
        Button(onClick = onSave, enabled = !busy && (editing || saving)) {
            Text(when {
                saving && busy -> "Guardando…"
                saving -> "Reintentar guardado"
                else -> "Guardar cambios"
            })
        }
        TextButton(onClick = { onRequestDeletion(true) }, enabled = !busy && editing) { Text("Eliminar movimiento") }
        TextButton(onClick = onBack, enabled = !busy) { Text("Volver a Movimientos") }
        if (!showingConfirmation) correction.error?.let { CashError(it) }
    }
    if (showingConfirmation) {
        val dismiss = if (deleting) onBack else ({ onRequestDeletion(false) })
        AlertDialog(
            onDismissRequest = { if (!busy) dismiss() },
            title = { Text("¿Eliminar movimiento?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Se eliminará el movimiento guardado y se actualizará el efectivo.")
                    correction.error?.let { CashError(it) }
                }
            },
            confirmButton = {
                TextButton(onClick = onDelete, enabled = !busy) {
                    Text(when {
                        busy -> "Eliminando…"
                        deleting -> "Reintentar eliminación"
                        else -> "Eliminar"
                    })
                }
            },
            dismissButton = {
                TextButton(onClick = dismiss, enabled = !busy) {
                    Text(if (deleting) "Volver a Movimientos" else "Cancelar")
                }
            },
        )
    }
}

@Composable
private fun MovementFields(form: MovementForm, enabled: Boolean, onEdit: (MovementForm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(form.amount, { onEdit(form.copy(amount = it)) }, Modifier.fillMaxWidth(),
            label = { Text("Importe (€)") }, enabled = enabled, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OutlinedTextField(form.date, { onEdit(form.copy(date = it)) }, Modifier.fillMaxWidth(),
            label = { Text("Fecha (DD/MM/AAAA)") }, enabled = enabled, singleLine = true)
        CategorySelector(form.category, enabled = enabled) { category ->
            onEdit(form.copy(category = checkNotNull(category)))
        }
        OutlinedTextField(form.note, { onEdit(form.copy(note = it)) }, Modifier.fillMaxWidth(),
            label = { Text("Nota (opcional)") }, enabled = enabled)
    }
}

@Composable
private fun CategorySelector(
    category: MovementCategory?,
    enabled: Boolean = true,
    allCategories: Boolean = false,
    onSelect: (MovementCategory?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
            Text("Categoría: ${category?.label ?: "Todas"}")
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            if (allCategories) {
                DropdownMenuItem(text = { Text("Todas") }, onClick = {
                    expanded = false
                    onSelect(null)
                })
            }
            MovementCategory.entries.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = {
                    expanded = false
                    onSelect(option)
                })
            }
        }
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
