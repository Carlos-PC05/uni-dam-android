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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.example.aplicacion.ui.theme.AplicacionTheme
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import kotlinx.coroutines.CompletableDeferred
import com.example.aplicacion.data.auth.AuthFailure
import org.junit.Rule
import org.junit.Test

/** Verifica el formulario visible y la conservación exacta de los caracteres introducidos. */
class AuthScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    /** El registro ofrece cuatro campos y transmite las dos contraseñas sin normalizarlas. */
    @Test fun registrationKeepsExactPasswords() {
        val state = mutableStateOf(AuthUiState(access = AccessStatus.SIGNED_OUT))
        var submitted: Pair<String, String>? = null
        compose.setContent {
            AplicacionTheme {
                AuthScreen(state.value, true,
                    { state.value = state.value.copy(fullName = it) },
                    { state.value = state.value.copy(email = it) },
                    { password, confirmation -> submitted = password to confirmation }, {}, {})
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
                AuthScreen(AuthUiState(access = AccessStatus.SIGNED_OUT, busy = true), false, {}, {}, { _, _ -> }, {}, {})
            }
        }
        compose.onNodeWithText("Guardando…").assertIsNotEnabled()
        compose.onNodeWithText("¿No tienes cuenta? Regístrate").assertIsNotEnabled()
    }

    /** Login solo pide correo y contraseña, sin campos o proveedores fuera del ticket. */
    @Test fun loginOnlyRequiresEmailAndPassword() {
        compose.setContent {
            AplicacionTheme {
                AuthScreen(AuthUiState(access = AccessStatus.SIGNED_OUT), false, {}, {}, { _, _ -> }, {}, {})
            }
        }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(2)
        compose.onNodeWithText("Iniciar sesión").assertIsEnabled()
        captureScreen("vesta-login")
    }

    /** El diálogo precarga correo, muestra éxito y conserva las credenciales de login. */
    @Test fun recoveryKeepsLoginFormAfterSuccess() {
        showApp(ScreenAuthRepository())
        compose.onNodeWithContentDescription("Correo electrónico").performTextInput("alice@example.com")
        compose.onNodeWithContentDescription("Contraseña").performTextInput("secreto escrito")
        compose.onNodeWithText("¿Has olvidado tu contraseña?").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Correo de recuperación").assertTextContains("alice@example.com")
        compose.onNodeWithContentDescription("Correo de recuperación").performTextReplacement("bob@example.com")
        compose.onNodeWithText("Enviar correo").performClick()
        compose.onNodeWithText("Te hemos enviado un correo electrónico para que restaures tu contraseña").assertExists()
        captureScreen("vesta-recovery-success", dialog = true)
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithContentDescription("Correo electrónico").assertTextContains("alice@example.com")
        compose.onNodeWithText("Iniciar sesión").performScrollTo().performClick()
        compose.onNodeWithText("Hola, Alicia").assertExists()
    }

    /** El error de red queda en el diálogo y permite reintentar sin perder los datos. */
    @Test fun recoveryErrorCanRetryWithoutLosingForm() {
        val remote = ScreenAuthRepository().apply { recoveryFailure = true }
        showApp(remote)
        compose.onNodeWithContentDescription("Correo electrónico").performTextInput("alice@example.com")
        compose.onNodeWithContentDescription("Contraseña").performTextInput("secreto escrito")
        compose.onNodeWithText("¿Has olvidado tu contraseña?").performScrollTo().performClick()
        compose.onNodeWithText("Enviar correo").performClick()
        compose.onNodeWithText("Sin conexión. Comprueba la red y reintenta.").assertExists()
        compose.onNodeWithContentDescription("Correo de recuperación").assertTextContains("alice@example.com")
        captureScreen("vesta-recovery-error", dialog = true)
        compose.runOnIdle { remote.recoveryFailure = false }
        compose.onNodeWithText("Enviar correo").performClick()
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithText("Mostrar").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Contraseña").assertTextContains("secreto escrito")
    }

    /** El envío pendiente desactiva las acciones y la edición hasta la respuesta remota. */
    @Test fun recoveryLoadingBlocksDuplicateRequests() {
        val remote = ScreenAuthRepository().apply { recoveryGate = CompletableDeferred() }
        showApp(remote)
        compose.onNodeWithContentDescription("Correo electrónico").performTextInput("alice@example.com")
        compose.onNodeWithText("¿Has olvidado tu contraseña?").performScrollTo().performClick()
        compose.onNodeWithText("Enviar correo").performClick()
        compose.onNodeWithText("Enviando…").assertIsNotEnabled()
        compose.onNodeWithText("Cancelar").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Correo de recuperación").assertIsNotEnabled()
        captureScreen("vesta-recovery-loading", dialog = true)
        compose.runOnIdle { remote.recoveryGate!!.complete(Unit) }
        compose.onNodeWithText("Cerrar").assertIsEnabled()
    }

    /** Conecta la UI y el ViewModel reales con una frontera remota controlable. */
    private fun showApp(remote: ScreenAuthRepository) {
        val vm = AuthViewModel(remote)
        compose.setContent { AplicacionTheme { VestaApp(vm) } }
    }

    /** Las secciones navegan sin duplicarse y el cierre retira el grafo privado completo. */
    @Test fun privateNavigationAndSignOut() {
        var backReachedActivity = false
        compose.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.addCallback(activity, object : OnBackPressedCallback(true) {
                /** Observa la salida desde la raíz sin finalizar la actividad de la prueba. */
                override fun handleOnBackPressed() { backReachedActivity = true }
            })
        }
        val remote = ScreenAuthRepository().apply { userId = "alice" }
        showApp(remote)
        compose.onNodeWithText("Hola, Alicia").assertExists()
        compose.onNodeWithText("Movimientos").performClick()
        compose.onNodeWithText("Pendiente de implementación. Esta sección se incorporará en su ticket correspondiente.").assertExists()
        compose.onNodeWithText("Inversiones").performClick()
        compose.onNodeWithText("Ajustes").performClick()
        compose.onNodeWithText("alice@example.com").assertExists()
        compose.onNodeWithText("Cerrar sesión").performClick()
        compose.onNodeWithText("Iniciar sesión").assertExists()
        compose.onNodeWithText("alice@example.com").assertDoesNotExist()
        // El observador solo recibe Atrás si ningún destino del grafo anterior lo intercepta.
        androidx.test.espresso.Espresso.pressBackUnconditionally()
        compose.runOnIdle { assertTrue(backReachedActivity) }
        compose.onNodeWithText("alice@example.com").assertDoesNotExist()
        compose.onNodeWithText("Hola, Alicia").assertDoesNotExist()
        compose.onNodeWithContentDescription("Correo electrónico").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithContentDescription("Contraseña").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithContentDescription("Correo electrónico").performTextInput("bob@example.com")
        compose.onNodeWithContentDescription("Contraseña").performTextInput("secreto escrito")
        compose.onNodeWithText("Iniciar sesión").performScrollTo().performClick()
        compose.onNodeWithText("Hola, Bruno").assertExists()
        compose.onNodeWithText("Ajustes").performClick()
        compose.onNodeWithText("bob@example.com").assertExists()
        compose.onNodeWithText("alice@example.com").assertDoesNotExist()
    }

    /** Guarda una captura del composable para revisar la presentación sin marcos del emulador. */
    private fun captureScreen(name: String, dialog: Boolean = false) {
        compose.waitForIdle()
        val root = if (dialog) compose.onNode(isDialog()) else compose.onRoot()
        val bitmap = root.captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}


/** Simula cuentas y envío de correo; la navegación y el estado usados por las pruebas son reales. */
private class ScreenAuthRepository : AuthRepository {
    override var userId: String? = null
    var recoveryFailure = false
    var recoveryGate: CompletableDeferred<Unit>? = null

    /** Este recorrido usa cuentas existentes y no permite registrar otras. */
    override suspend fun register(email: String, password: String) = error("No se registra en esta prueba")

    /** Abre la cuenta elegida si el formulario conservó la contraseña de prueba. */
    override suspend fun signIn(email: String, password: String) {
        check(password == "secreto escrito")
        userId = if (email == "alice@example.com") "alice" else "bob"
    }

    /** Mantiene pendiente el envío o devuelve un fallo de red recuperable. */
    override suspend fun sendPasswordResetEmail(email: String) {
        recoveryGate?.await()
        if (recoveryFailure) throw AuthFailure("Sin conexión. Comprueba la red y reintenta.")
    }

    /** Devuelve el perfil de la cuenta activa, sin reutilizar el de la anterior. */
    override suspend fun loadProfile(): UserProfile = when (userId) {
        "alice" -> UserProfile("alice", "Alicia", "alice@example.com")
        "bob" -> UserProfile("bob", "Bruno", "bob@example.com")
        else -> error("No hay sesión")
    }

    /** El recorrido no modifica los perfiles. */
    override suspend fun saveProfile(fullName: String): UserProfile = error("No se guarda en esta prueba")

    /** Cierra localmente la sesión, sin necesitar conexión. */
    override fun signOut() { userId = null }
}
