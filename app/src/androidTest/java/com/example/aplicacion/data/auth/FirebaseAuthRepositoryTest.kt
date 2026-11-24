package com.example.aplicacion.data.auth

import androidx.test.platform.app.InstrumentationRegistry
import com.example.aplicacion.data.cash.FilePendingMovementStore
import com.example.aplicacion.data.cash.FirebaseInitialBalanceRepository
import com.example.aplicacion.data.cash.FirebaseMovementRepository
import com.example.aplicacion.data.cash.MovementChanged
import com.example.aplicacion.data.cash.MovementRejected
import com.example.aplicacion.model.Movement
import com.example.aplicacion.model.MovementCategory
import com.example.aplicacion.model.InitialBalance
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

/** Prueba el repositorio Android real contra Authentication y Firestore locales. */
class FirebaseAuthRepositoryTest {
    /** Alta, perfil, saldo, reapertura y separación de dos cuentas con los SDK reales. */
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
        val cash = FirebaseInitialBalanceRepository(db)
        val movements = FirebaseMovementRepository(db)
        val email = "test-${UUID.randomUUID()}@example.com"
        val password = "prueba segura 123@"
        try {
            repo.register(email, password)
            val firstUid = checkNotNull(repo.userId)
            assertNull(repo.loadProfile())
            val profile = repo.saveProfile("Carlos de prueba")
            assertEquals(profile, repo.loadProfile())
            assertNull(cash.load(firstUid))
            val balance = InitialBalance(-1250, LocalDate.of(2024, 1, 1))
            assertEquals(balance, cash.create(firstUid, balance))
            assertEquals(balance, cash.create(firstUid, balance.copy(cents = 0)))
            val income = Movement(UUID.randomUUID().toString(), 50000, balance.date, MovementCategory.INCOME, "")
            val pending = FilePendingMovementStore(context)
            pending.save(firstUid, income)
            try {
                movements.create(firstUid, income)
                val reopenedStore = FilePendingMovementStore(context)
                assertNull(reopenedStore.load("otra-$firstUid"))
                movements.create(firstUid, checkNotNull(reopenedStore.load(firstUid)))
                try {
                    movements.create(firstUid, income.copy(id = UUID.randomUUID().toString(), date = balance.date.minusDays(1)))
                    fail("La fecha anterior al saldo debe rechazarse")
                } catch (_: MovementRejected) { }
            } finally {
                pending.clear(firstUid)
            }
            assertNull(FilePendingMovementStore(context).load(firstUid))
            assertEquals(listOf(income), movements.load(firstUid))
            val corrected = income.copy(cents = 2200, date = balance.date.plusMonths(1),
                category = MovementCategory.FOOD, revision = UUID.randomUUID().toString())
            movements.update(firstUid, income, income)
            assertEquals(listOf(income), movements.load(firstUid))
            try {
                movements.update(firstUid, income, corrected.copy(date = LocalDate.of(9999, 1, 1)))
                fail("Firestore debe rechazar la fecha futura sin modificar el original")
            } catch (_: MovementRejected) { }
            movements.update(firstUid, income, corrected)
            movements.update(firstUid, income, corrected)
            assertEquals(listOf(corrected), movements.load(firstUid))
            try {
                movements.delete(firstUid, income)
                fail("Una versión anterior no puede eliminar una corrección confirmada")
            } catch (_: MovementChanged) { }
            val restored = income.copy(revision = UUID.randomUUID().toString())
            movements.update(firstUid, corrected, restored)
            try {
                movements.update(firstUid, income, corrected)
                fail("Restaurar los valores anteriores no restaura su revisión")
            } catch (_: MovementChanged) { }
            movements.delete(firstUid, restored)
            movements.delete(firstUid, restored)
            assertTrue(movements.load(firstUid).isEmpty())
            try {
                movements.create(firstUid, income)
                fail("Un alta pendiente no puede recuperar un identificador eliminado")
            } catch (_: MovementRejected) { }
            try {
                movements.update(firstUid, corrected, restored)
                fail("Editar no puede volver a crear un movimiento eliminado")
            } catch (_: MovementChanged) { }
            val remaining = income.copy(id = UUID.randomUUID().toString())
            movements.create(firstUid, remaining)
            repo.signOut()
            assertNull(repo.userId)
            try {
                repo.signIn(email, "incorrecta")
                fail("Las credenciales incorrectas no deben abrir sesión")
            } catch (_: AuthFailure) { assertNull(repo.userId) }
            repo.sendPasswordReset(email)
            repo.sendPasswordReset("unknown-$email")
            assertNull(repo.userId)
            repo.signIn(email, password)
            assertEquals(profile, repo.loadProfile())
            assertEquals(balance, cash.load(firstUid))
            repo.signOut()
            repo.register("second-$email", password)
            assertNotEquals(firstUid, repo.userId)
            assertNull(repo.loadProfile())
            repo.saveProfile("Segunda cuenta")
            val secondUid = checkNotNull(repo.userId)
            assertNull(cash.load(secondUid))
            assertEquals(emptyList<Movement>(), movements.load(secondUid))
            val foreignOperations: List<suspend () -> Unit> = listOf(
                { movements.load(firstUid) },
                { movements.update(firstUid, remaining, corrected.copy(id = remaining.id)) },
                { movements.delete(firstUid, remaining) },
            )
            for (operation in foreignOperations) {
                try {
                    operation()
                    fail("Otra cuenta no puede acceder a los movimientos anteriores")
                } catch (error: FirebaseFirestoreException) {
                    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error.code)
                }
            }
            try {
                db.collection("usuarios").document(firstUid).get().await()
                fail("Otra cuenta no puede leer el perfil anterior")
            } catch (error: FirebaseFirestoreException) {
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error.code)
            }
        } finally {
            repo.signOut()
            db.terminate().await()
            app.delete()
        }
    }
}
