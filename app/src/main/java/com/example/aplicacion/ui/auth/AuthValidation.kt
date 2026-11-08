package com.example.aplicacion.ui.auth

/** Campos cuyos errores se muestran junto al formulario correspondiente. */
enum class AuthField { NAME, EMAIL, PASSWORD, CONFIRMATION }

/** Comprueba el correo antes de enviarlo; Authentication realiza la validación definitiva. */
fun validateLogin(email: String, password: String): Map<AuthField, String> = buildMap {
    if (!email.trim().matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
        put(AuthField.EMAIL, "Introduce un correo electrónico válido.")
    }
    if (password.isEmpty()) put(AuthField.PASSWORD, "Introduce tu contraseña.")
}

/** Valida nombre, correo y contraseña sin modificar los caracteres de las credenciales. */
fun validateRegistration(name: String, email: String, password: String, confirmation: String): Map<AuthField, String> =
    validateLogin(email, password).toMutableMap().apply {
        if (name.trim().isEmpty() || name.trim().length > 100) {
            put(AuthField.NAME, "El nombre debe tener entre 1 y 100 caracteres.")
        }
        if (password.length < 12 || password.length > 4096 ||
            password.none { it.isLetter() } || password.none { it in '0'..'9' } ||
            password.none { it in "-_?#@." }) {
            put(AuthField.PASSWORD, "Usa entre 12 y 4096 caracteres, con una letra, un número y un símbolo de -_?#@.")
        }
        if (password != confirmation || confirmation.isEmpty()) {
            put(AuthField.CONFIRMATION, "Las contraseñas deben coincidir exactamente.")
        }
    }
