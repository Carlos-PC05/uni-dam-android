package com.example.aplicacion.ui.auth

import com.example.aplicacion.data.auth.AuthRepository
import com.example.aplicacion.model.UserProfile
import com.example.aplicacion.ui.navigation.VestaApp
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.aplicacion.ui.theme.AplicacionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Verifica el formulario visible y la conservación exacta de los caracteres introducidos. */
class AuthScreenTest {
    @get:Rule val compose = createComposeRule()

    /** El registro ofrece cuatro campos y transmite las dos contraseñas sin normalizarlas. */
    @Test fun registrationKeepsExactPasswords() {
        val state = mutableStateOf(AuthUiState(access = AccessStatus.SIGNED_OUT))
        var submitted: Pair<String, String>? = null
        compose.setContent {
            AplicacionTheme {
                AuthScreen(state.value, true,
                    { state.value = state.value.copy(fullName = it) },
                    { state.value = state.value.copy(email = it) },
                    { password, confirmation -> submitted = password to confirmation }, {})
            }
        }
        captureScreen("vesta-register")
        compose.onNodeWithContentDescription("Nombre completo").performTextInput("Carlos")
        compose.onNodeWithContentDescription("Correo electrónico").performTextInput("carlos@example.com")
        compose.onNodeWithContentDescription("Contraseña").performScrollTo().performTextInput("mi frase segura 1@ ")
        compose.onNodeWithContentDescription("Confirmar contraseña").performScrollTo().performTextInput("mi frase segura 1@ ")
        compose.onNodeWithText("Crear cuenta").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("mi frase segura 1@ " to "mi frase segura 1@ ", submitted) }
    }

    /** Durante el guardado no se permite reenviar ni cambiar de formulario. */
    @Test fun loadingDisablesSubmitAndNavigation() {
        compose.setContent {
            AplicacionTheme {
                AuthScreen(AuthUiState(access = AccessStatus.SIGNED_OUT, busy = true), false, {}, {}, { _, _ -> }, {})
            }
        }
        compose.onNodeWithText("Guardando…").assertIsNotEnabled()
        compose.onNodeWithText("¿No tienes cuenta? Regístrate").assertIsNotEnabled()
    }

    /** Login solo pide correo y contraseña, sin campos o proveedores fuera del ticket. */
    @Test fun loginOnlyRequiresEmailAndPassword() {
        compose.setContent {
            AplicacionTheme {
                AuthScreen(AuthUiState(access = AccessStatus.SIGNED_OUT), false, {}, {}, { _, _ -> }, {})
            }
        }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(2)
        compose.onNodeWithText("Iniciar sesión").assertIsEnabled()
        captureScreen("vesta-login")
    }

    /** Las secciones navegan sin duplicarse y el cierre retira el grafo privado completo. */
    @Test fun privateNavigationAndSignOut() {
        val remote = object : AuthRepository {
            override var userId: String? = "alice"
            /** Esta prueba parte de una cuenta existente. */
            override suspend fun register(email: String, password: String) = error("No se registra en esta prueba")
            /** Esta prueba parte de una sesión existente. */
            override suspend fun signIn(email: String, password: String) = error("No se autentica en esta prueba")
            /** Recupera el perfil sintético del recorrido de navegación. */
            override suspend fun loadProfile(): UserProfile = UserProfile("alice", "Alicia", "alice@example.com")
            /** El recorrido no modifica perfiles. */
            override suspend fun saveProfile(fullName: String): UserProfile = error("No se guarda en esta prueba")
            /** Retira la sesión sintética al salir de Ajustes. */
            override fun signOut() { userId = null }
        }
        val vm = AuthViewModel(remote)
        compose.setContent { AplicacionTheme { VestaApp(vm) } }
        compose.onNodeWithText("Hola, Alicia").assertExists()
        compose.onNodeWithText("Movimientos").performClick()
        compose.onNodeWithText("Pendiente de implementación. Esta sección se incorporará en su ticket correspondiente.").assertExists()
        compose.onNodeWithText("Inversiones").performClick()
        compose.onNodeWithText("Ajustes").performClick()
        compose.onNodeWithText("alice@example.com").assertExists()
        compose.onNodeWithText("Cerrar sesión").performClick()
        compose.onNodeWithText("Iniciar sesión").assertExists()
        compose.onNodeWithText("alice@example.com").assertDoesNotExist()
    }

    /** Guarda una captura del composable para revisar la presentación sin marcos del emulador. */
    private fun captureScreen(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
