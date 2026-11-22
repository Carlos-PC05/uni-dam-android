package com.example.aplicacion.ui.inicio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.aplicacion.model.UserProfile
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/** Muestra el efectivo confirmado o guía al usuario para configurar su primera referencia. */
@Composable
fun HomeScreen(
    profile: UserProfile,
    state: InitialBalanceUiState,
    onAmountChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onProfile: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Hola, ${profile.fullName}", style = MaterialTheme.typography.headlineMedium)
        val balance = state.balance
        when {
            balance != null -> {
                val euros = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-ES"))
                    .format(BigDecimal.valueOf(balance.cents, 2))
                Text("Efectivo", style = MaterialTheme.typography.titleLarge)
                Text(euros, style = MaterialTheme.typography.headlineMedium)
                Text("Saldo inicial al comienzo del ${balance.date.format(calendarDateFormat)}")
            }
            state.loaded -> {
                Text("Configura tu saldo inicial", style = MaterialTheme.typography.titleLarge)
                Text("Indica cuánto efectivo tenías al comienzo del día elegido, antes de registrar operaciones.")
                OutlinedTextField(
                    value = state.amount,
                    onValueChange = onAmountChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Importe (€)") },
                    supportingText = { Text(state.amountError ?: "Admite cero y cantidades negativas.") },
                    isError = state.amountError != null,
                    singleLine = true,
                    enabled = !state.busy,
                )
                OutlinedTextField(
                    value = state.date,
                    onValueChange = onDateChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Fecha inicial") },
                    supportingText = { Text(state.dateError ?: "DD/MM/AAAA") },
                    isError = state.dateError != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    enabled = !state.busy,
                )
                Button(onClick = onSave, enabled = !state.busy) {
                    Text(if (state.busy) "Guardando…" else "Guardar saldo inicial")
                }
            }
            state.message == null -> {
                CircularProgressIndicator()
                Text("Consultando el saldo…")
            }
        }
        state.message?.let { message ->
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (!state.loaded && !state.busy) Button(onClick = onRetry) { Text("Reintentar") }
        }
        Button(onClick = onProfile) { Text("Ver mi perfil") }
    }
}
