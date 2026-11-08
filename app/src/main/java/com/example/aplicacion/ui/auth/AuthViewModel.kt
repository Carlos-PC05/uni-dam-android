package com.example.aplicacion.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aplicacion.data.auth.AuthRepository
import com.example.aplicacion.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** Estados de acceso: una sesión sin perfil completo todavía no permite entrar. */
enum class AccessStatus { CHECKING, SIGNED_OUT, INCOMPLETE, READY, FAILED }

/** Estado visible sin contraseñas; los secretos solo viven en el formulario y la petición activa. */
data class AuthUiState(
    val access: AccessStatus = AccessStatus.CHECKING,
    val busy: Boolean = false,
    val profile: UserProfile? = null,
    val fullName: String = "",
    val email: String = "",
    val fieldErrors: Map<AuthField, String> = emptyMap(),
    val message: String? = null,
)

/** Coordina la sesión y el perfil privado sin exponer Firebase a los composables. */
@HiltViewModel
class AuthViewModel @Inject constructor(private val repository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state = _state.asStateFlow()

    init { restoreSession() }

    /** Conserva el nombre durante errores y recreaciones del formulario. */
    fun updateName(value: String) { _state.update { it.copy(fullName = value, fieldErrors = it.fieldErrors - AuthField.NAME) } }

    /** Conserva el correo; las contraseñas nunca forman parte de este estado. */
    fun updateEmail(value: String) { _state.update { it.copy(email = value, fieldErrors = it.fieldErrors - AuthField.EMAIL) } }

    /** Retira errores anteriores al cambiar entre login y registro. */
    fun clearErrors() { _state.update { it.copy(fieldErrors = emptyMap(), message = null) } }

    /** Recupera la sesión persistente y comprueba el perfil contra el servidor. */
    fun restoreSession() {
        if (_state.value.busy) return
        if (repository.userId == null) {
            _state.value = AuthUiState(access = AccessStatus.SIGNED_OUT)
            return
        }
        _state.update { it.copy(access = AccessStatus.CHECKING, profile = null) }
        perform(AccessStatus.FAILED) { loadProfile() }
    }

    /** Valida y registra; un fallo del perfil conserva la cuenta para reintentar solo ese paso. */
    fun register(password: String, confirmation: String) {
        if (_state.value.busy) return
        val form = _state.value
        val errors = validateRegistration(form.fullName, form.email, password, confirmation)
        _state.update { it.copy(fieldErrors = errors, message = null) }
        if (errors.isNotEmpty()) return
        perform(AccessStatus.SIGNED_OUT) {
            repository.register(form.email.trim(), password)
            _state.update { it.copy(access = AccessStatus.INCOMPLETE) }
            val profile = withTimeout(30_000) { repository.saveProfile(form.fullName) }
            _state.value = AuthUiState(access = AccessStatus.READY, profile = profile)
        }
    }

    /** Abre sesión y permite entrar únicamente después de recuperar un perfil completo. */
    fun signIn(password: String) {
        if (_state.value.busy) return
        val form = _state.value
        val errors = validateLogin(form.email, password)
        _state.update { it.copy(fieldErrors = errors, message = null) }
        if (errors.isNotEmpty()) return
        perform(AccessStatus.SIGNED_OUT) {
            repository.signIn(form.email.trim(), password)
            loadProfile()
        }
    }

    /** Completa o reintenta el perfil de la cuenta existente sin registrar otra cuenta. */
    fun completeProfile() {
        if (_state.value.busy) return
        val name = _state.value.fullName.trim()
        if (name.isEmpty() || name.length > 100) {
            _state.update { it.copy(fieldErrors = mapOf(AuthField.NAME to "El nombre debe tener entre 1 y 100 caracteres.")) }
            return
        }
        perform(AccessStatus.INCOMPLETE) {
            val profile = withTimeout(30_000) { repository.saveProfile(name) }
            _state.value = AuthUiState(access = AccessStatus.READY, profile = profile)
        }
    }

    /** Cierra sesión y descarta todo el estado de la cuenta anterior. */
    fun signOut() {
        if (_state.value.busy) return
        repository.signOut()
        _state.value = AuthUiState(access = AccessStatus.SIGNED_OUT)
    }

    /** Distingue documento inexistente de lectura fallida; esta última se propaga. */
    private suspend fun loadProfile() {
        val profile = withTimeout(30_000) { repository.loadProfile() }
        _state.update { it.copy(
            access = if (profile == null) AccessStatus.INCOMPLETE else AccessStatus.READY,
            profile = profile,
        ) }
    }

    /** Serializa acciones y conserva el formulario; solo el perfil admite una espera limitada. */
    private fun perform(failureAccess: AccessStatus, action: suspend () -> Unit) {
        _state.update { it.copy(busy = true, message = null, fieldErrors = emptyMap()) }
        viewModelScope.launch {
            try {
                // Cancelar await no cancela el alta del SDK: no liberar el envío antes de su respuesta.
                action()
            } catch (timeout: TimeoutCancellationException) {
                showFailure(failureAccess, "No se ha podido confirmar la operación. Comprueba la conexión y reintenta.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showFailure(failureAccess, (error as? com.example.aplicacion.data.auth.AuthFailure)?.message
                    ?: "No se ha podido completar la operación. Comprueba la conexión y reintenta.")
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    /** Evita volver a registrar una cuenta que ya existe tras fallar el guardado del perfil. */
    private fun showFailure(fallback: AccessStatus, message: String) {
        val access = when {
            repository.userId == null -> AccessStatus.SIGNED_OUT
            fallback == AccessStatus.FAILED -> AccessStatus.FAILED
            _state.value.access == AccessStatus.INCOMPLETE -> AccessStatus.INCOMPLETE
            else -> AccessStatus.FAILED
        }
        _state.update { it.copy(access = access, profile = null, message = message) }
    }
}
