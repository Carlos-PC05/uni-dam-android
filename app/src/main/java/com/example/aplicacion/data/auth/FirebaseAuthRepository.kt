package com.example.aplicacion.data.auth

import com.example.aplicacion.model.UserProfile
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.CancellationException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/** Accede exclusivamente al documento usuarios/{uid} de la sesión autenticada. */
class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : AuthRepository {
    override val userId: String? get() = auth.currentUser?.uid

    /** Delega el alta y la protección de credenciales en Firebase Authentication. */
    override suspend fun register(email: String, password: String) {
        remoteCall { auth.createUserWithEmailAndPassword(email, password).await() }
    }

    /** Abre una sesión con correo y contraseña verificados por Authentication. */
    override suspend fun signIn(email: String, password: String) {
        remoteCall { auth.signInWithEmailAndPassword(email, password).await() }
    }

    /** Lee del servidor para no confundir un fallo de red con un perfil inexistente. */
    override suspend fun loadProfile(): UserProfile? {
        val user = checkNotNull(auth.currentUser) { "No hay una sesión activa." }

        val doc = firestore.collection("usuarios")
            .document(user.uid)
            .get(Source.SERVER)
            .let { remoteCall { it.await() } }

        if (!doc.exists()) return null

        val name = checkNotNull(doc.getString("nombreCompleto")) { "El perfil no contiene un nombre válido." }

        check(name.isNotBlank() && name.length <= 100) { "El perfil contiene un nombre inválido." }

        return UserProfile(user.uid, name, checkNotNull(user.email))
    }

    /** Reutiliza el uid como documento: reintentar el guardado no crea perfiles duplicados. */
    override suspend fun saveProfile(fullName: String): UserProfile {
        val user = checkNotNull(auth.currentUser) { "No hay una sesión activa." }

        val name = fullName.trim()

        require(name.isNotEmpty() && name.length <= 100) { "El nombre debe tener entre 1 y 100 caracteres." }

        firestore.collection("usuarios")
            .document(user.uid)
            .set(mapOf("nombreCompleto" to name))
            .let { remoteCall { it.await() } }

        return UserProfile(user.uid, name, checkNotNull(user.email))
    }

    /** Retira las credenciales de sesión; los formularios se limpian al cambiar de pantalla raíz. */
    override fun signOut() = auth.signOut()

    /** Traduce errores del SDK; preserva la cancelación de las corutinas. */
    private suspend fun <T> remoteCall(action: suspend () -> T): T = try {
        action()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: FirebaseAuthException) {
        throw AuthFailure(when (error.errorCode) {
            "ERROR_EMAIL_ALREADY_IN_USE" -> "Ese correo ya está registrado. Inicia sesión con tu cuenta."
            "ERROR_WEAK_PASSWORD" -> "La contraseña no cumple la política de seguridad."
            "ERROR_INVALID_EMAIL" -> "El correo electrónico no es válido."
            "ERROR_USER_DISABLED" -> "La cuenta está deshabilitada."
            "ERROR_TOO_MANY_REQUESTS" -> "Demasiados intentos. Espera unos minutos antes de reintentar."
            else -> "No se ha podido autenticar la cuenta. Comprueba el correo y la contraseña."
        })
    } catch (error: FirebaseNetworkException) {
        throw AuthFailure("No hay conexión con Firebase. Comprueba la red y reintenta.")
    }
}
