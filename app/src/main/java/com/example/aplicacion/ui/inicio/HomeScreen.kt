package com.example.aplicacion.ui.inicio

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aplicacion.model.UserProfile

/** Da acceso al perfil sin presentar como terminadas las funciones financieras pendientes. */
@Composable
fun HomeScreen(profile: UserProfile, onProfile: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp)) {
        Text("Hola, ${profile.fullName}", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text("Tu cuenta está lista. El saldo y el resumen financiero se incorporarán en los siguientes tickets.")
        Spacer(Modifier.height(24.dp))
        Button(onClick = onProfile) { Text("Ver mi perfil") }
    }
}
