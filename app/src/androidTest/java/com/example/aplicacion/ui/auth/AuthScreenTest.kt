package com.example.aplicacion.ui.auth

import com.example.aplicacion.data.auth.AuthRepository
import com.example.aplicacion.data.cash.InitialBalanceRepository
import com.example.aplicacion.data.cash.PendingMovementStore
import com.example.aplicacion.model.InitialBalance
import com.example.aplicacion.ui.inicio.InitialBalanceViewModel
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import com.example.aplicacion.model.UserProfile
import com.example.aplicacion.ui.navigation.VestaApp
import com.example.aplicacion.data.cash.MovementRepository
import com.example.aplicacion.model.Movement
import com.example.aplicacion.model.MovementCategory
import com.example.aplicacion.ui.movimientos.MovementsViewModel
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
                AuthScreen(AuthUiState(access = AccessStatus.SIGNED_OUT, busy = true), false, {}, {}, { _, _ -> }, {}, onPasswordReset = {})
            }
        }
        compose.onNodeWithText("Recuperar contraseña").assertIsNotEnabled()
        compose.onNodeWithText("Enviando solicitud…").assertIsNotEnabled()
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
            override suspend fun signIn(email: String, password: String) {
                assertEquals("bob@example.com", email)
                assertEquals("second-password", password)
                userId = "bob"
            }

            override suspend fun sendPasswordReset(email: String) {
                assertEquals("bob@example.com", email)
            }

            override suspend fun loadProfile(): UserProfile = when (userId) {
                "alice" -> UserProfile("alice", "Alicia", "alice@example.com")
                "bob" -> UserProfile("bob", "Bruno", "bob@example.com")
                else -> error("No hay sesión activa")
            }
            /** El recorrido no modifica perfiles. */
            override suspend fun saveProfile(fullName: String): UserProfile = error("No se guarda en esta prueba")
            /** Retira la sesión sintética al salir de Ajustes. */
            override fun signOut() { userId = null }
        }
        val vm = AuthViewModel(remote)
        var aliceBalance: InitialBalance? = null
        val balances = object : InitialBalanceRepository {
            override suspend fun load(uid: String): InitialBalance? =
                if (uid == "alice") aliceBalance else null

            override suspend fun create(uid: String, balance: InitialBalance): InitialBalance =
                error("El recorrido solo consulta el saldo")
        }
        var aliceMovement: Movement? = null
        val movementRepository = object : MovementRepository {
            override suspend fun load(uid: String): List<Movement> =
                if (uid == "alice") listOfNotNull(aliceMovement) else emptyList()

            override suspend fun create(uid: String, movement: Movement) =
                error("El recorrido solo consulta los movimientos")

            override suspend fun update(uid: String, original: Movement, replacement: Movement) {
                assertEquals("alice", uid)
                assertEquals(aliceMovement, original)
                assertEquals(original.id, replacement.id)
                aliceMovement = replacement
            }

            override suspend fun delete(uid: String, movement: Movement) {
                assertEquals("alice", uid)
                assertEquals(aliceMovement, movement)
                aliceMovement = null
            }
        }
        val pendingStore = object : PendingMovementStore {
            override suspend fun load(uid: String): Movement? = null
            override suspend fun save(uid: String, movement: Movement) = error("El recorrido solo consulta movimientos")
            override suspend fun clear(uid: String) = error("El recorrido solo consulta movimientos")
        }
        compose.setContent {
            AplicacionTheme {
                VestaApp(
                    vm,
                    balanceViewModel = { remember { InitialBalanceViewModel(balances) } },
                    cashViewModel = { graph ->
                        viewModel(viewModelStoreOwner = graph) {
                            MovementsViewModel(movementRepository, balances, pendingStore)
                        }
                    },
                )
            }
        }
        compose.onNodeWithText("Hola, Alicia").assertExists()
        compose.onNodeWithText("Configura tu saldo inicial").assertExists()
        compose.runOnIdle {
            aliceBalance = InitialBalance(-1250, LocalDate.of(2024, 1, 1))
            aliceMovement = Movement("alice-income", 500, LocalDate.now(), MovementCategory.INCOME, "Abono")
        }
        compose.onNodeWithText("Movimientos").performClick()
        compose.onNodeWithText("Categoría: Todas").performClick()
        compose.onNodeWithText("Comida").performClick()
        compose.onNodeWithText("No hay movimientos para estos filtros.").assertExists()
        compose.onNodeWithText("Categoría: Comida").performClick()
        compose.onNodeWithText("Ingreso").performClick()
        compose.onNodeWithText("Editar movimiento").performClick()
        compose.onNode(hasSetTextAction() and hasText("5.00")).performTextReplacement("10,50")
        compose.onNodeWithText("Guardar cambios").performScrollTo().performClick()
        compose.onNodeWithText("+10,50", substring = true).assertExists()
        compose.onNodeWithText("Editar movimiento").performClick()
        compose.onNodeWithText("Eliminar movimiento").performScrollTo().performClick()
        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle { assertEquals(1050L, aliceMovement?.cents) }
        compose.onNodeWithText("Eliminar movimiento").performScrollTo().performClick()
        compose.onNodeWithText("Eliminar", useUnmergedTree = true).performClick()
        compose.onNodeWithText("No hay movimientos para estos filtros.").assertExists()
        compose.onNodeWithText("Inicio").performClick()
        compose.onNodeWithText("-12,50", substring = true).assertExists()
        compose.onNodeWithText("Configura tu saldo inicial").assertDoesNotExist()
        compose.onNodeWithText("Inversiones").performClick()
        compose.onNodeWithText("Ajustes").performClick()
        compose.onNodeWithText("alice@example.com").assertExists()
        compose.onNodeWithText("Cerrar sesión").performClick()
        compose.onNodeWithText("Iniciar sesión").assertExists()
        compose.onNodeWithText("alice@example.com").assertDoesNotExist()
        compose.onNodeWithContentDescription("Correo electrónico").performTextInput("bob@example.com")
        compose.onNodeWithContentDescription("Contraseña").performScrollTo().performTextInput("second-password")
        compose.onNodeWithText("Recuperar contraseña").performScrollTo().performClick()
        compose.onNodeWithText("Si hay una cuenta con ese correo,", substring = true).assertExists()
        compose.onNodeWithText("Iniciar sesión").performScrollTo().performClick()
        compose.onNodeWithText("Hola, Bruno").assertExists()
        compose.onNodeWithText("Configura tu saldo inicial").assertExists()
        compose.onNodeWithText("-12,50", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Hola, Alicia").assertDoesNotExist()
    }

    /** Guarda una captura del composable para revisar la presentación sin marcos del emulador. */
    private fun captureScreen(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
