package com.example.aplicacion.ui.auth

import com.example.aplicacion.data.auth.AuthRepository
import com.example.aplicacion.model.UserProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import com.example.aplicacion.data.auth.AuthFailure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/** Comprueba el estado público usando un proveedor remoto controlado, sin Firebase real. */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    /** Sustituye Main por un reloj controlable para observar cargas y reintentos. */
    @Before fun setMainDispatcher() { Dispatchers.setMain(StandardTestDispatcher()) }
    /** Restituye el dispatcher para no afectar a otras pruebas. */
    @After fun resetMainDispatcher() { Dispatchers.resetMain() }

    /** Una instalación sin sesión presenta acceso y no pantallas privadas. */
    @Test fun startsSignedOut() = runTest {
        val vm = AuthViewModel(FakeAuthRepository())
        runCurrent()
        assertEquals(AccessStatus.SIGNED_OUT, vm.state.value.access)
    }
    /** Una sesión completa se recupera sin solicitar credenciales de nuevo. */
    @Test fun restoresExistingSession() = runTest {
        val remote = FakeAuthRepository().apply {
            userId = "alice"
            profile = UserProfile("alice", "Alicia", "alice@example.com")
        }
        val vm = AuthViewModel(remote)
        runCurrent()
        assertEquals(remote.profile, vm.state.value.profile)
        assertEquals(AccessStatus.READY, vm.state.value.access)
    }

    /** Un fallo de lectura no se interpreta como ausencia del perfil. */
    @Test fun failedProfileReadRequiresRetry() = runTest {
        val remote = FakeAuthRepository().apply { userId = "alice"; readFailure = true }
        val vm = AuthViewModel(remote)
        runCurrent()
        assertEquals(AccessStatus.FAILED, vm.state.value.access)
        assertNull(vm.state.value.profile)
        remote.readFailure = false
        vm.restoreSession()
        runCurrent()
        assertEquals(AccessStatus.INCOMPLETE, vm.state.value.access)
    }

    /** El alta completa abre Inicio sin un segundo login. */
    @Test fun registrationCreatesCompleteSession() = runTest {
        val remote = FakeAuthRepository()
        val vm = AuthViewModel(remote)
        vm.updateName(" Alicia ")
        vm.updateEmail(" alice@example.com ")
        vm.register("abcdefghij1@", "abcdefghij1@")
        runCurrent()
        assertEquals(AccessStatus.READY, vm.state.value.access)
        assertEquals("Alicia", vm.state.value.profile?.fullName)
    }

    /** Una confirmación incorrecta no crea una cuenta ni pierde el formulario. */
    @Test fun invalidFormDoesNotCreateAccount() = runTest {
        val remote = FakeAuthRepository()
        val vm = AuthViewModel(remote)
        vm.updateName("Alicia")
        vm.updateEmail("alice@example.com")
        vm.register("abcdefghij1@", "otra")
        runCurrent()
        assertNull(remote.userId)
        assertEquals("Alicia", vm.state.value.fullName)
        assertTrue(vm.state.value.fieldErrors.containsKey(AuthField.CONFIRMATION))
    }

    /** El fallo de guardado conserva la cuenta y el nombre para reintentar el perfil. */
    @Test fun failedProfileSaveCanRetryWithoutAnotherAccount() = runTest {
        val remote = FakeAuthRepository().apply { saveFailure = true }
        val vm = AuthViewModel(remote)
        vm.updateName("Alicia")
        vm.updateEmail("alice@example.com")
        vm.register("abcdefghij1@", "abcdefghij1@")
        runCurrent()
        assertEquals(AccessStatus.INCOMPLETE, vm.state.value.access)
        assertEquals("Alicia", vm.state.value.fullName)
        remote.saveFailure = false
        vm.completeProfile()
        runCurrent()
        assertEquals(AccessStatus.READY, vm.state.value.access)
        assertEquals(1, remote.registrations)
    }

    /** Al reabrir una cuenta sin perfil se solicita completar el nombre. */
    @Test fun incompleteSessionDoesNotEnterHome() = runTest {
        val vm = AuthViewModel(FakeAuthRepository().apply { userId = "alice" })
        runCurrent()
        assertEquals(AccessStatus.INCOMPLETE, vm.state.value.access)
        assertNull(vm.state.value.profile)
    }

    /** Un proveedor que rechaza credenciales deja el correo y no concede acceso. */
    @Test fun rejectedCredentialsKeepForm() = runTest {
        val vm = AuthViewModel(FakeAuthRepository().apply { signInFailure = true })
        vm.updateEmail("alice@example.com")
        vm.signIn("incorrecta")
        runCurrent()
        assertEquals(AccessStatus.SIGNED_OUT, vm.state.value.access)
        assertEquals("alice@example.com", vm.state.value.email)
        assertNotNull(vm.state.value.message)
        assertFalse(vm.state.value.busy)
    }

    /** Repetir la pulsación durante la red no duplica registros. */
    @Test fun repeatedSubmitWaitsForFirstRequest() = runTest {
        val remote = FakeAuthRepository().apply { gate = CompletableDeferred() }
        val vm = AuthViewModel(remote)
        vm.updateName("Alicia")
        vm.updateEmail("alice@example.com")
        vm.register("abcdefghij1@", "abcdefghij1@")
        vm.register("abcdefghij1@", "abcdefghij1@")
        runCurrent()
        assertTrue(vm.state.value.busy)
        assertEquals(1, remote.registrations)
        remote.gate!!.complete(Unit)
        runCurrent()
        assertEquals(AccessStatus.READY, vm.state.value.access)
    }

    /** El SDK puede seguir procesando el alta: un reloj local no debe permitir duplicarla. */
    @Test fun slowAuthenticationRemainsSerialized() = runTest {
        val remote = FakeAuthRepository().apply { gate = CompletableDeferred() }
        val vm = AuthViewModel(remote)
        vm.updateName("Alicia"); vm.updateEmail("alice@example.com")
        vm.register("abcdefghij1@", "abcdefghij1@")
        runCurrent()
        advanceTimeBy(31_000)
        runCurrent()
        assertTrue(vm.state.value.busy)
        vm.register("abcdefghij1@", "abcdefghij1@")
        remote.gate!!.complete(Unit)
        runCurrent()
        assertEquals(1, remote.registrations)
        assertEquals(AccessStatus.READY, vm.state.value.access)
    }

    /** Cerrar sesión retira el perfil y los campos privados antes de otra cuenta. */
    @Test fun signOutClearsPrivateState() = runTest {
        val remote = FakeAuthRepository().apply {
            userId = "alice"; profile = UserProfile("alice", "Alicia", "alice@example.com")
        }
        val vm = AuthViewModel(remote)
        runCurrent()
        vm.signOut()
        assertNull(remote.userId)
        assertEquals(AuthUiState(access = AccessStatus.SIGNED_OUT), vm.state.value)
    }

    /** Solicitar recuperación acepta el correo del diálogo sin contraseña ni nueva sesión. */
    @Test fun recoverySendsEmailWithoutChangingLogin() = runTest {
        val remote = FakeAuthRepository()
        val vm = AuthViewModel(remote)
        vm.updateEmail("alice@example.com")
        vm.openRecovery()
        assertEquals("alice@example.com", vm.state.value.recovery?.email)
        vm.updateRecoveryEmail(" bob@example.com ")
        vm.recoverPassword()
        runCurrent()
        assertEquals(listOf("bob@example.com"), remote.recoveryRequests)
        assertTrue(vm.state.value.recovery!!.sent)
        assertEquals("alice@example.com", vm.state.value.email)
        assertEquals(AccessStatus.SIGNED_OUT, vm.state.value.access)
        assertNull(remote.userId)
        assertNull(vm.state.value.profile)
        assertFalse(vm.state.value.busy)
    }

    /** Un correo inválido no llega al proveedor ni se interpreta como envío correcto. */
    @Test fun recoveryRejectsInvalidEmail() = runTest {
        val remote = FakeAuthRepository()
        val vm = AuthViewModel(remote)
        vm.openRecovery()
        vm.updateRecoveryEmail("correo incorrecto")
        vm.recoverPassword()
        runCurrent()
        assertTrue(remote.recoveryRequests.isEmpty())
        assertNotNull(vm.state.value.recovery?.emailError)
        assertFalse(vm.state.value.recovery!!.sent)
        assertFalse(vm.state.value.busy)
    }

    /** Un fallo remoto conserva ambos correos y permite reintentar sin reiniciar login. */
    @Test fun recoveryFailureKeepsFormAndCanRetry() = runTest {
        val remote = FakeAuthRepository().apply { recoveryFailure = true }
        val vm = AuthViewModel(remote)
        vm.updateEmail("alice@example.com")
        vm.openRecovery()
        vm.updateRecoveryEmail("bob@example.com")
        vm.recoverPassword()
        runCurrent()
        assertEquals("Sin conexión.", vm.state.value.recovery?.error)
        assertEquals("bob@example.com", vm.state.value.recovery?.email)
        assertEquals("alice@example.com", vm.state.value.email)
        assertFalse(vm.state.value.recovery!!.sent)
        assertFalse(vm.state.value.busy)
        remote.recoveryFailure = false
        vm.recoverPassword()
        runCurrent()
        assertTrue(vm.state.value.recovery!!.sent)
        assertNull(vm.state.value.recovery?.error)
        vm.closeRecovery()
        assertNull(vm.state.value.recovery)
        assertEquals("alice@example.com", vm.state.value.email)
    }

    /** Una respuesta lenta no permite reenviar, cerrar ni alterar el destinatario pendiente. */
    @Test fun recoveryWaitsForProviderBeforeAllowingAnotherRequest() = runTest {
        val remote = FakeAuthRepository().apply { gate = CompletableDeferred() }
        val vm = AuthViewModel(remote)
        vm.updateEmail("alice@example.com")
        vm.openRecovery()
        vm.recoverPassword()
        runCurrent()
        advanceTimeBy(31_000)
        runCurrent()
        vm.recoverPassword()
        vm.closeRecovery()
        vm.updateRecoveryEmail("bob@example.com")
        vm.signIn("incorrecta")
        assertTrue(vm.state.value.busy)
        assertEquals("alice@example.com", vm.state.value.recovery?.email)
        assertEquals(listOf("alice@example.com"), remote.recoveryRequests)
        assertNull(remote.userId)
        remote.gate!!.complete(Unit)
        runCurrent()
        assertTrue(vm.state.value.recovery!!.sent)
        assertFalse(vm.state.value.busy)
        vm.recoverPassword()
        assertEquals(1, remote.recoveryRequests.size)
    }

    /** Cambiar el correo tras el éxito retira la confirmación que correspondía al anterior. */
    @Test fun editingRecoveryEmailClearsPreviousResult() = runTest {
        val vm = AuthViewModel(FakeAuthRepository())
        vm.updateEmail("alice@example.com")
        vm.openRecovery()
        vm.recoverPassword()
        runCurrent()
        vm.updateRecoveryEmail("bob@example.com")
        assertEquals(PasswordRecoveryState("bob@example.com"), vm.state.value.recovery)
    }

    /** La recuperación no puede abrirse desde una sesión privada ni alterar su perfil. */
    @Test fun recoveryDoesNotReplaceAuthenticatedSession() = runTest {
        val remote = FakeAuthRepository().apply {
            userId = "alice"
            profile = UserProfile("alice", "Alicia", "alice@example.com")
        }
        val vm = AuthViewModel(remote)
        runCurrent()
        val ready = vm.state.value
        vm.openRecovery()
        vm.recoverPassword()
        runCurrent()
        assertEquals(ready, vm.state.value)
        assertTrue(remote.recoveryRequests.isEmpty())
    }

    /** Un correo duplicado se muestra como error sin dar acceso ni perder los datos. */
    @Test fun duplicateEmailDoesNotEnterHome() = runTest {
        val vm = AuthViewModel(FakeAuthRepository().apply { registrationFailure = true })
        vm.updateName("Alicia"); vm.updateEmail("alice@example.com")
        vm.register("abcdefghij1@", "abcdefghij1@")
        runCurrent()
        assertEquals(AccessStatus.SIGNED_OUT, vm.state.value.access)
        assertEquals("alice@example.com", vm.state.value.email)
        assertNotNull(vm.state.value.message)
    }

}

/** Simula únicamente la frontera del proveedor externo para controlar red y sesión. */
private class FakeAuthRepository : AuthRepository {
    override var userId: String? = null
    var profile: UserProfile? = null
    val recoveryRequests = mutableListOf<String>()
    var registrations = 0
    var readFailure = false
    var saveFailure = false
    var signInFailure = false
    var registrationFailure = false
    var recoveryFailure = false
    var gate: CompletableDeferred<Unit>? = null

    /** Simula un alta remota que puede bloquearse o rechazar un correo duplicado. */
    override suspend fun register(email: String, password: String) {
        registrations++
        if (registrationFailure) throw AuthFailure("Ese correo ya está registrado.")
        gate?.await()
        userId = "alice"
    }
    /** Simula la autenticación sin aplicar reglas locales a contraseñas existentes. */
    override suspend fun signIn(email: String, password: String) {
        if (signInFailure) throw AuthFailure("Credenciales incorrectas.")
        userId = "alice"
    }
    /** Registra las solicitudes de recuperación sin abrir ninguna sesión. */
    override suspend fun sendPasswordResetEmail(email: String) {
        recoveryRequests += email
        gate?.await()
        if (recoveryFailure) throw AuthFailure("Sin conexión.")
    }

    /** Distingue un documento ausente de un fallo de red. */
    override suspend fun loadProfile(): UserProfile? {
        if (readFailure) throw AuthFailure("Sin conexión.")
        return profile
    }
    /** Devuelve el perfil solo cuando se confirma el guardado simulado. */
    override suspend fun saveProfile(fullName: String): UserProfile {
        if (saveFailure) throw AuthFailure("Sin conexión.")
        return UserProfile("alice", fullName.trim(), "alice@example.com").also { profile = it }
    }
    /** Retira la sesión simulada. */
    override fun signOut() { userId = null }
}
