package com.example.aplicacion.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Completa la cuenta ya autenticada sin volver a pedir ni crear credenciales. */
@Composable
fun CompleteProfileScreen(state: AuthUiState, onName: (String) -> Unit, onSave: () -> Unit, onSignOut: () -> Unit) {
    AuthPanel("Completa tu perfil") {
        Text("Tu cuenta ya existe. Confirma tu nombre para terminar el registro.")
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(value = state.fullName, onValueChange = onName, label = { Text("Nombre completo") },
            enabled = !state.busy, singleLine = true, modifier = Modifier.fillMaxWidth(),
            isError = state.fieldErrors.containsKey(AuthField.NAME))
        state.fieldErrors[AuthField.NAME]?.let { AuthError(it) }
        Spacer(Modifier.height(16.dp))
        AuthError(state.message)
        Button(onClick = onSave, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.busy) "Guardando…" else "Guardar perfil")
        }
        TextButton(onClick = onSignOut, enabled = !state.busy) { Text("Volver al acceso") }
    }
}
