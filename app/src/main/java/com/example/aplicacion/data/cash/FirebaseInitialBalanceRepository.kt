package com.example.aplicacion.data.cash

import com.example.aplicacion.model.InitialBalance
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

class FirebaseInitialBalanceRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : InitialBalanceRepository {
    override suspend fun load(uid: String): InitialBalance? =
        reference(uid).get(Source.SERVER).await().toBalance()

    override suspend fun create(uid: String, balance: InitialBalance): InitialBalance {
        val reference = reference(uid)
        // Una transacción recupera la referencia ya confirmada si se repite el alta.
        return firestore.runTransaction { transaction ->
            transaction.get(reference).toBalance() ?: balance.also {
                transaction.set(reference, mapOf("centimos" to it.cents, "dia" to it.date.toEpochDay()))
            }
        }.await()
    }

    private fun reference(uid: String) =
        firestore.collection("usuarios").document(uid).collection("efectivo").document("saldoInicial")

    private fun DocumentSnapshot.toBalance(): InitialBalance? {
        if (!exists()) return null
        val cents = checkNotNull(getLong("centimos"))
        val date = LocalDate.ofEpochDay(checkNotNull(getLong("dia")))
        return InitialBalance(cents, date)
    }
}
