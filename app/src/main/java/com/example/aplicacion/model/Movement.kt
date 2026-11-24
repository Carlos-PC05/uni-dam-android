package com.example.aplicacion.model

import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth

enum class MovementCategory(val label: String) {
    INCOME("Ingreso"), LEISURE("Ocio"), HOUSING("Vivienda"), FOOD("Comida"),
    TRANSPORT("Transporte"), HEALTH("Salud"), OTHER("Otros"),
}

data class Movement(
    val id: String,
    val cents: Long,
    val date: LocalDate,
    val category: MovementCategory,
    val note: String,
    val revision: String? = null,
)

data class CashFlow(val income: BigInteger, val expenses: BigInteger) {
    val net: BigInteger get() = income - expenses
}

/** Suma céntimos exactos, incluso cuando el total excede el rango de un importe individual. */
fun Iterable<Movement>.cashFlow(month: YearMonth? = null): CashFlow {
    var income = BigInteger.ZERO
    var expenses = BigInteger.ZERO
    for (movement in this) {
        if (month != null && YearMonth.from(movement.date) != month) continue
        if (movement.category == MovementCategory.INCOME) {
            income += BigInteger.valueOf(movement.cents)
        } else {
            expenses += BigInteger.valueOf(movement.cents)
        }
    }
    return CashFlow(income, expenses)
}
