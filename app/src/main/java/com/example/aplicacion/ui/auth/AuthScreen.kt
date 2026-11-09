package com.example.aplicacion.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.aplicacion.ui.theme.*

/** Presenta login o registro; las contraseñas permanecen solo en memoria del formulario. */
@Composable
fun AuthScreen(
    state: AuthUiState,
    registering: Boolean,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onSubmit: (String, String) -> Unit,
    onSwitch: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    AuthPanel(if (registering) "Crea tu cuenta" else "Bienvenido") {
        Text(if (registering) "Empieza a organizar tus finanzas." else "Tus finanzas, en un mismo lugar.",
            style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        if (registering) {
            AuthTextField("Nombre completo", state.fullName, onNameChange, state.fieldErrors[AuthField.NAME], !state.busy)
        }
        AuthTextField("Correo electrónico", state.email, onEmailChange, state.fieldErrors[AuthField.EMAIL], !state.busy,
            keyboardType = KeyboardType.Email)
        PasswordField("Contraseña", password, { password = it }, state.fieldErrors[AuthField.PASSWORD], !state.busy)
        if (registering) {
            Text("Mínimo 12 caracteres: letras, números y un símbolo de -_?#@.",
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            PasswordField("Confirmar contraseña", confirmation, { confirmation = it },
                state.fieldErrors[AuthField.CONFIRMATION], !state.busy)
        }
        AuthError(state.message)
        Spacer(Modifier.height(20.dp))
        Button(onClick = { onSubmit(password, confirmation) }, enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp)) {
            if (state.busy) {
                CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            }
            Text(if (state.busy) "Guardando…" else if (registering) "Crear cuenta" else "Iniciar sesión")
        }
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onSwitch, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (registering) "¿Ya tienes cuenta? Inicia sesión" else "¿No tienes cuenta? Regístrate")
        }
    }
}

/** Contenedor adaptativo con cabecera oscura y panel redondeado desplazable sobre el teclado. */
@Composable
fun AuthPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Forest).safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 28.dp)) {
                Text("Vesta", style = MaterialTheme.typography.titleMedium, color = Mint)
                Spacer(Modifier.height(12.dp))
                Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Cream)
            }
            Column(Modifier.weight(1f).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Mint).verticalScroll(rememberScrollState()).padding(28.dp), content = content)
        }
    }
}

/** Campo de texto con etiqueta, error asociado y contraste legible sobre el fondo oscuro. */
@Composable
private fun AuthTextField(
    label: String, value: String, onChange: (String) -> Unit, error: String?, enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    transformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null,
) {
    Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    TextField(value = value, onValueChange = onChange, enabled = enabled,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = transformation, trailingIcon = trailing,
        shape = RoundedCornerShape(14.dp), isError = error != null,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Forest, unfocusedContainerColor = Forest,
            disabledContainerColor = Forest, errorContainerColor = Forest,
            focusedTextColor = Cream, unfocusedTextColor = Cream, disabledTextColor = Cream,
            errorTextColor = Cream, cursorColor = Mint,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent, errorIndicatorColor = MaterialTheme.colorScheme.error,
        ),
        // La etiqueta externa también se asigna como nombre accesible del campo.
        placeholder = { Text(label, color = Cream.copy(alpha = 0.8f)) },
    )
    if (error != null) Text(error, color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    Spacer(Modifier.height(16.dp))
}

/** Permite mostrar la contraseña sin alterar su contenido ni guardarla de forma persistente. */
@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit, error: String?, enabled: Boolean) {
    var visible by remember { mutableStateOf(false) }
    AuthTextField(label, value, onChange, error, enabled, KeyboardType.Password,
        if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailing = {
            TextButton(onClick = { visible = !visible }, enabled = enabled) {
                Text(if (visible) "Ocultar" else "Mostrar", color = Cream)
            }
        })
}

/** Anuncia el error de la operación sin retirar el formulario que permite recuperarse. */
@Composable
fun AuthError(message: String?) {
    if (message != null) Text(message, color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
}
