package com.example.aplicacion.model

import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class CashFlowTest {
    @Test
    fun calculaEfectivoYFlujoEnCentimos() {
        val date = LocalDate.of(2026, 1, 1)
        val movements = listOf(
            Movement("1", 50_000, date, MovementCategory.INCOME, ""),
            Movement("2", 20_000, date, MovementCategory.FOOD, ""),
        )
        val flow = movements.cashFlow(YearMonth.from(date))
        assertEquals(BigInteger.valueOf(50_000), flow.income)
        assertEquals(BigInteger.valueOf(20_000), flow.expenses)
        assertEquals(BigInteger.valueOf(30_000), flow.net)
        assertEquals(BigInteger.valueOf(130_000), BigInteger.valueOf(100_000) + movements.cashFlow().net)
        assertEquals(CashFlow(BigInteger.ZERO, BigInteger.ZERO), movements.cashFlow(YearMonth.from(date).plusMonths(1)))
    }

    @Test
    fun conservaCentimosAunqueLaSumaExcedaLong() {
        val date = LocalDate.of(2026, 1, 1)
        val movements = listOf(
            Movement("1", Long.MAX_VALUE, date, MovementCategory.INCOME, ""),
            Movement("2", 2, date, MovementCategory.INCOME, ""),
            Movement("3", 1, date, MovementCategory.FOOD, ""),
        )
        assertEquals(BigInteger("9223372036854775808"), movements.cashFlow().net)
    }
}
