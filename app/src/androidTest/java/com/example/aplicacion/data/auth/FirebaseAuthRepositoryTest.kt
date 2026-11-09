package com.example.aplicacion.data.auth

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/** Prueba el repositorio Android real contra Authentication y Firestore locales. */
class FirebaseAuthRepositoryTest {
    /** Comprueba perfil, cambio de contraseña, cierre y aislamiento con los SDK reales. */
    @Test fun accountAndProfileRoundTrip() = runBlocking {
        assumeTrue("Requiere emuladores Firebase y el argumento firebaseEmulators=true",
            InstrumentationRegistry.getArguments().getString("firebaseEmulators") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
            .setProjectId("demo-vesta").setApplicationId("1:123:android:test")
            .setApiKey("fake-api-key").build(), "test-${UUID.randomUUID()}")
        val auth = FirebaseAuth.getInstance(app).apply { useEmulator("127.0.0.1", 9099) }
        val db = FirebaseFirestore.getInstance(app).apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
            useEmulator("127.0.0.1", 8080)
        }
        val repo = FirebaseAuthRepository(auth, db)
        val email = "test-${UUID.randomUUID()}@example.com"
        val password = "prueba segura 123@"
        try {
            repo.register(email, password)
            val firstUid = repo.userId
            assertNotNull(firstUid)
            assertNull(repo.loadProfile())
            val profile = repo.saveProfile("Carlos de prueba")
            assertEquals(profile, repo.loadProfile())
            repo.signOut()
            assertNull(repo.userId)
            repo.sendPasswordResetEmail(email)
            assertNull(repo.userId)
            val code = passwordResetCode(email)
            assertEquals(email, auth.verifyPasswordResetCode(code).await())
            val newPassword = "nueva prueba segura 456@"
            auth.confirmPasswordReset(code, newPassword).await()
            try {
                repo.signIn(email, password)
                fail("La contraseña anterior al cambio no debe abrir sesión")
            } catch (_: AuthFailure) { assertNull(repo.userId) }
            repo.signIn(email, newPassword)
            assertEquals(profile, repo.loadProfile())
            repo.signOut()
            repo.sendPasswordResetEmail("missing-$email")
            assertNull(repo.userId)
            repo.register("second-$email", password)
            assertNotEquals(firstUid, repo.userId)
            assertNull(repo.loadProfile())
            repo.saveProfile("Segunda cuenta")
            try {
                db.collection("usuarios").document(firstUid!!).get().await()
                fail("Otra cuenta no puede leer el perfil anterior")
            } catch (error: com.google.firebase.firestore.FirebaseFirestoreException) {
                assertEquals(com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED, error.code)
            }
        } finally {
            repo.signOut()
            db.terminate().await()
            app.delete()
        }
    }

    /** Obtiene el enlace ficticio del emulador local, sin acceder a ningún buzón ni servicio remoto. */
    private fun passwordResetCode(email: String): String {
        val connection = URL("http://127.0.0.1:9099/emulator/v1/projects/demo-vesta/oobCodes")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        try {
            check(connection.responseCode == 200) { "No se pudieron consultar los correos emulados" }
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val codes = JSONObject(response).getJSONArray("oobCodes")
            for (index in codes.length() - 1 downTo 0) {
                val code = codes.getJSONObject(index)
                if (code.getString("email") == email && code.getString("requestType") == "PASSWORD_RESET") {
                    return code.getString("oobCode")
                }
            }
            error("No se generó el correo de recuperación de la cuenta de prueba")
        } finally {
            connection.disconnect()
        }
    }
}
