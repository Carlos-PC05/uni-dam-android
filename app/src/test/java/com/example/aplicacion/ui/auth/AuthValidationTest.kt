package com.example.aplicacion.ui.auth

import org.junit.Assert.*
import org.junit.Test

/** Verifica las reglas de contraseña acordadas a través de la validación pública. */
class AuthValidationTest {
    /** Una contraseña válida tiene doce caracteres y los tres grupos exigidos. */
    @Test fun acceptsTwelveCharacters() {
        assertTrue(validateRegistration("Carlos", "carlos@example.com", "abcdefghij1@", "abcdefghij1@").isEmpty())
    }

    /** Rechaza cada requisito ausente, incluido el límite de once caracteres. */
    @Test fun rejectsInvalidPasswords() {
        listOf("abcdefghi1@", "abcdefghijk@", "12345678901@", "abcdefghijk1", "a1@".repeat(1366)).forEach {
            assertTrue("Debe rechazarse la contraseña del caso", validateRegistration("Carlos", "c@example.com", it, it).containsKey(AuthField.PASSWORD))
        }
    }

    /** Los espacios y otros símbolos están permitidos; no se normaliza la contraseña. */
    @Test fun acceptsSpacesAndOtherCharacters() {
        val password = "una frase larga 1@!"
        assertTrue(validateRegistration("Carlos", "c@example.com", password, password).isEmpty())
    }

    /** Cada símbolo de la lista satisface el requisito sin exigir mayúsculas. */
    @Test fun acceptsEachRequiredSymbol() {
        "-_?#@.".forEach { symbol ->
            val password = "abcdefghij1$symbol"
            assertTrue(validateRegistration("Carlos", "c@example.com", password, password).isEmpty())
        }
    }

    /** La confirmación distingue espacios, mayúsculas y el campo vacío. */
    @Test fun confirmationMustMatchExactly() {
        listOf("Abcdefghij1@", "abcdefghij1@ ", "").forEach {
            assertTrue(validateRegistration("Carlos", "c@example.com", "abcdefghij1@", it).containsKey(AuthField.CONFIRMATION))
        }
    }

    /** Nombre y correo inválidos generan errores en sus campos, no solicitudes remotas. */
    @Test fun rejectsInvalidIdentity() {
        val errors = validateRegistration("  ", "correo incorrecto", "abcdefghij1@", "abcdefghij1@")
        assertEquals(setOf(AuthField.NAME, AuthField.EMAIL), errors.keys)
    }

    /** El login permite contraseñas previas: la política de alta no sustituye a Authentication. */
    @Test fun loginDoesNotEnforceRegistrationPolicy() {
        assertTrue(validateLogin(" c@example.com ", "antigua").isEmpty())
        assertEquals(setOf(AuthField.EMAIL, AuthField.PASSWORD), validateLogin("", "").keys)
    }
}
