package com.example.aplicacion.data.cash

import com.example.aplicacion.model.InitialBalance

interface InitialBalanceRepository {
    /** Consulta la referencia remota. Devuelve null si no existe y propaga los fallos de lectura. */
    suspend fun load(uid: String): InitialBalance?

    /** Crea la referencia si está vacía y devuelve la confirmada, sin sobrescribir una existente. */
    suspend fun create(uid: String, balance: InitialBalance): InitialBalance
}
