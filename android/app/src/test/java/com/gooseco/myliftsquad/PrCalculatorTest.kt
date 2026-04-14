package com.gooseco.myliftsquad

import com.gooseco.myliftsquad.data.PrCalculator
import com.gooseco.myliftsquad.data.db.CompetitionEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrCalculatorTest {

    private fun entry(
        squat: Double? = null,
        bench: Double? = null,
        deadlift: Double? = null,
        total: Double? = null,
        place: String? = "1"
    ) = CompetitionEntry(
        athleteSlug = "test-athlete",
        date = "2024-01-01",
        meetName = "Test Meet",
        federation = "IPF",
        equipment = "Raw",
        division = "Open",
        weightClassKg = "93",
        bodyweightKg = 90.0,
        best3SquatKg = squat,
        best3BenchKg = bench,
        best3DeadliftKg = deadlift,
        totalKg = total,
        place = place,
        dots = null,
        meetCountry = "GBR",
        meetTown = null
    )

    @Test
    fun `best total is included in PR calculation`() {
        val entries = listOf(
            entry(squat = 200.0, bench = 130.0, deadlift = 250.0, total = 580.0),
            entry(squat = 210.0, bench = 135.0, deadlift = 260.0, total = 605.0)
        )
        val result = PrCalculator.calculate(entries)
        assertEquals(605.0, result.bestTotal)
    }

    @Test
    fun `new competition with higher lifts updates all PRs including total`() {
        val withNewComp = listOf(
            entry(squat = 200.0, bench = 130.0, deadlift = 250.0, total = 580.0),
            entry(squat = 215.0, bench = 140.0, deadlift = 270.0, total = 625.0)
        )
        val result = PrCalculator.calculate(withNewComp)
        assertEquals(215.0, result.bestSquat)
        assertEquals(140.0, result.bestBench)
        assertEquals(270.0, result.bestDeadlift)
        assertEquals(625.0, result.bestTotal)
    }

    @Test
    fun `disqualified entries are excluded from PR calculation`() {
        val entries = listOf(
            entry(squat = 200.0, bench = 130.0, deadlift = 250.0, total = 580.0, place = "1"),
            entry(squat = 300.0, bench = 200.0, deadlift = 350.0, total = 850.0, place = "DQ"),
            entry(squat = 300.0, bench = 200.0, deadlift = 350.0, total = 850.0, place = "DD"),
            entry(place = "DNS"),
            entry(place = "NS"),
            entry(place = "G")
        )
        val result = PrCalculator.calculate(entries)
        assertEquals(200.0, result.bestSquat)
        assertEquals(130.0, result.bestBench)
        assertEquals(250.0, result.bestDeadlift)
        assertEquals(580.0, result.bestTotal)
    }

    @Test
    fun `negative values from bomb-outs are excluded`() {
        val entries = listOf(
            entry(squat = 200.0, bench = 130.0, deadlift = 250.0, total = 580.0, place = "1"),
            entry(squat = -1.0, bench = 130.0, deadlift = 250.0, total = null, place = "2")
        )
        val result = PrCalculator.calculate(entries)
        assertEquals(200.0, result.bestSquat)
    }

    @Test
    fun `empty entries return null PRs`() {
        val result = PrCalculator.calculate(emptyList())
        assertNull(result.bestSquat)
        assertNull(result.bestBench)
        assertNull(result.bestDeadlift)
        assertNull(result.bestTotal)
    }
}
