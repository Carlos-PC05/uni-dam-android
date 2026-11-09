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

/** Prueba el repositorio Android real contra Authentication y Firestore locales. */
class FirebaseAuthRepositoryTest {
    /** Alta, perfil, cierre, login y separación de dos cuentas usando los SDK reales. */
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
            try {
                repo.signIn(email, "incorrecta")
                fail("Las credenciales incorrectas no deben abrir sesión")
            } catch (_: AuthFailure) { assertNull(repo.userId) }
            repo.signIn(email, password)
            assertEquals(profile, repo.loadProfile())
            repo.signOut()
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
}
