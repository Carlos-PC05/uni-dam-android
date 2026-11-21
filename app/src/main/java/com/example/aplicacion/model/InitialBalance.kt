package com.example.aplicacion.model

import java.time.LocalDate

/** Efectivo en euros al comienzo de una fecha de calendario, con precisión de céntimos. */
data class InitialBalance(val cents: Long, val date: LocalDate)
