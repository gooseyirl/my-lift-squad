package com.gooseco.myliftsquad.data

import com.gooseco.myliftsquad.data.db.CompetitionEntry

object PrCalculator {

    private val disqualifiedPlaces = setOf("DQ", "DD", "DNS", "NS", "G")

    data class Result(
        val bestSquat: Double?,
        val bestBench: Double?,
        val bestDeadlift: Double?,
        val bestTotal: Double?
    )

    fun calculate(entries: List<CompetitionEntry>): Result {
        val valid = entries.filter { it.place !in disqualifiedPlaces }
        return Result(
            bestSquat = valid.mapNotNull { it.best3SquatKg?.takeIf { v -> v > 0 } }.maxOrNull(),
            bestBench = valid.mapNotNull { it.best3BenchKg?.takeIf { v -> v > 0 } }.maxOrNull(),
            bestDeadlift = valid.mapNotNull { it.best3DeadliftKg?.takeIf { v -> v > 0 } }.maxOrNull(),
            bestTotal = valid.mapNotNull { it.totalKg?.takeIf { v -> v > 0 } }.maxOrNull()
        )
    }
}
