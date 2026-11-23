package com.example.aplicacion.ui.ajustes

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aplicacion.model.UserProfile

/** Muestra únicamente el perfil de la sesión actual y permite abandonar la cuenta. */
@Composable
fun ProfileScreen(profile: UserProfile, onSignOut: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp)) {
        Text("Mi perfil", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        Text("Nombre completo", style = MaterialTheme.typography.labelLarge)
        Text(profile.fullName, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Text("Correo electrónico", style = MaterialTheme.typography.labelLarge)
        Text(profile.email)
        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = onSignOut) { Text("Cerrar sesión") }
        Text("El recordatorio se incorporará en su ticket correspondiente.", style = MaterialTheme.typography.bodySmall)
    }
}
