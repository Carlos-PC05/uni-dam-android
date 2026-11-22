package com.example.aplicacion.data.cash

import com.example.aplicacion.model.Movement
import com.example.aplicacion.model.MovementCategory
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

interface MovementRepository {
    /** Consulta los movimientos confirmados de una cuenta. */
    suspend fun load(uid: String): List<Movement>

    /** Crea el movimiento una vez por identificador y espera la confirmación remota. */
    suspend fun create(uid: String, movement: Movement)
}

class FirebaseMovementRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : MovementRepository {
    override suspend fun load(uid: String): List<Movement> =
        collection(uid).get(Source.SERVER).await().documents.map { document ->
            Movement(
                document.id,
                checkNotNull(document.getLong("centimos")),
                LocalDate.ofEpochDay(checkNotNull(document.getLong("dia"))),
                MovementCategory.valueOf(checkNotNull(document.getString("categoria"))),
                checkNotNull(document.getString("nota")),
            )
        }

    /** Un reintento con el mismo identificador conserva el documento ya confirmado. */
    override suspend fun create(uid: String, movement: Movement) {
        val reference = collection(uid).document(movement.id)
        firestore.runTransaction { transaction ->
            if (!transaction.get(reference).exists()) {
                transaction.set(reference, mapOf(
                    "centimos" to movement.cents,
                    "dia" to movement.date.toEpochDay(),
                    "categoria" to movement.category.name,
                    "nota" to movement.note,
                ))
            }
        }.await()
    }

    private fun collection(uid: String) =
        firestore.collection("usuarios").document(uid).collection("movimientos")
}
