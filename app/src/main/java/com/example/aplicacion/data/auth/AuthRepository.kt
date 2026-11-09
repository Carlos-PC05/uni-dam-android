package com.example.aplicacion.data.auth

import com.example.aplicacion.model.UserProfile

/** Frontera con el proveedor remoto; permite probar el flujo sin una cuenta real. */
interface AuthRepository {
    val userId: String?
    /** Crea una cuenta y mantiene la sesión iniciada por Authentication. */
    // suspend para que la función sea asíncrona (coroutines)
    suspend fun register(email: String, password: String)
    /** Autentica las credenciales y conserva la sesión del proveedor. */
    suspend fun signIn(email: String, password: String)
    /** Consulta el perfil remoto; devuelve null únicamente si el documento no existe. */
    suspend fun loadProfile(): UserProfile?
    /** Guarda el nombre del usuario actual y espera confirmación remota. */
    suspend fun saveProfile(fullName: String): UserProfile
    /** Cierra la sesión local del proveedor. */
    fun signOut()
}

/** Fallo del proveedor traducido a un mensaje comprensible sin exponer credenciales. */
class AuthFailure(message: String) : Exception(message)
