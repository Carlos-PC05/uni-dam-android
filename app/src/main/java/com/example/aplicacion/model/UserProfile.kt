package com.example.aplicacion.model

/** Perfil privado; el correo procede de Authentication y la contraseña nunca se almacena aquí. */
data class UserProfile(val uid: String, val fullName: String, val email: String)
