package com.example.aplicacion.data.cash

import com.example.aplicacion.model.Movement
import com.example.aplicacion.model.MovementCategory
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

interface MovementRepository {
    /** Consulta los movimientos confirmados de una cuenta. */
    suspend fun load(uid: String): List<Movement>

    /** Crea el movimiento una vez por identificador y espera la confirmación remota. */
    suspend fun create(uid: String, movement: Movement)

    /**
     * Confirma una corrección con el mismo ID y una revisión nueva, o reconoce esa revisión ya guardada.
     * Si los datos coinciden con el original, confirma su revisión sin escribir.
     * Lanza [MovementRejected] si Firestore rechaza la escritura y confirma que sigue el original.
     * Lanza [MovementChanged] si el documento ya no está en la revisión original ni en la solicitada.
     */
    suspend fun update(uid: String, original: Movement, replacement: Movement)

    /**
     * Elimina la revisión original y considera confirmada la ausencia del documento.
     * Lanza [MovementChanged] si hay otra revisión activa.
     */
    suspend fun delete(uid: String, movement: Movement)
}

/** Firestore rechazó la escritura y una lectura remota confirmó que el intento no se aplicó. */
class MovementRejected(cause: Exception? = null) : Exception(cause)

/** La versión remota ya no coincide con la que se abrió para corregir. */
class MovementChanged : Exception()

class FirebaseMovementRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : MovementRepository {
    override suspend fun load(uid: String): List<Movement> =
        collection(uid).get(Source.SERVER).await().documents.map { it.toMovement() }

    /** Un reintento con el mismo identificador conserva el documento ya confirmado. */
    override suspend fun create(uid: String, movement: Movement) {
        val reference = collection(uid).document(movement.id)
        try {
            firestore.runTransaction { transaction ->
                if (!transaction.get(reference).exists()) {
                    transaction.set(reference, movement.fields())
                }
            }.await()
        } catch (error: FirebaseFirestoreException) {
            if (!error.isWriteRejection()) throw error

            // Un intento anterior pudo confirmarse aunque la aplicación dejara de esperar.
            if (!reference.get(Source.SERVER).await().exists()) throw MovementRejected(error)
        }
    }

    override suspend fun update(uid: String, original: Movement, replacement: Movement) {
        require(original.id == replacement.id)
        val revision = replacement.revision
        require(replacement == original || (revision != null && revision != original.revision))
        val reference = collection(uid).document(original.id)
        val confirmed = try {
            firestore.runTransaction { transaction ->
                when (transaction.get(reference).toMovementOrNull()) {
                    replacement -> true // El primer intento pudo guardarse antes de perder la respuesta.
                    original -> {
                        transaction.update(reference, replacement.fields() + ("revision" to checkNotNull(revision)))
                        true
                    }
                    else -> false
                }
            }.await()
        } catch (error: FirebaseFirestoreException) {
            if (!error.isWriteRejection()) throw error
            val saved = reference.get(Source.SERVER).await().toMovementOrNull()
            if (saved == original) throw MovementRejected(error)
            saved == replacement
        }
        if (!confirmed) throw MovementChanged()
    }

    private fun FirebaseFirestoreException.isWriteRejection() =
        code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
            code == FirebaseFirestoreException.Code.INVALID_ARGUMENT

    private fun DocumentSnapshot.toMovementOrNull() = if (exists()) toMovement() else null

    override suspend fun delete(uid: String, movement: Movement) {
        val reference = collection(uid).document(movement.id)
        val confirmed = firestore.runTransaction { transaction ->
            val snapshot = transaction.get(reference)
            when {
                !snapshot.exists() -> true
                snapshot.toMovement() != movement -> false
                else -> {
                    // El marcador solo conserva el identificador para invalidar reintentos de alta.
                    val marker = firestore.collection("usuarios").document(uid)
                        .collection("movimientosEliminados").document(movement.id)
                    transaction.set(marker, mapOf("eliminado" to true))
                    transaction.delete(reference)
                    true
                }
            }
        }.await()
        if (!confirmed) throw MovementChanged()
    }

    private fun DocumentSnapshot.toMovement() = Movement(
        id,
        checkNotNull(getLong("centimos")),
        LocalDate.ofEpochDay(checkNotNull(getLong("dia"))),
        MovementCategory.valueOf(checkNotNull(getString("categoria"))),
        checkNotNull(getString("nota")),
        getString("revision"),
    )

    private fun Movement.fields() = mapOf(
        "centimos" to cents,
        "dia" to date.toEpochDay(),
        "categoria" to category.name,
        "nota" to note,
    )

    private fun collection(uid: String) =
        firestore.collection("usuarios").document(uid).collection("movimientos")
}
