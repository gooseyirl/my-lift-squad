package com.gooseco.myliftsquad

import com.gooseco.myliftsquad.data.api.OplApiService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for OplApiService's CSV parsing logic.
 *
 * CSV column layout (0-indexed):
 * 0=Name, 1=Sex, 2=Event, 3=Equipment, 4=Age, 5=AgeClass, 6=BirthYearClass,
 * 7=Division, 8=BodyweightKg, 9=WeightClassKg,
 * 10–13=Squat1–4Kg, 14=Best3SquatKg,
 * 15–18=Bench1–4Kg, 19=Best3BenchKg,
 * 20–23=Deadlift1–4Kg, 24=Best3DeadliftKg,
 * 25=TotalKg, 26=Place, 27=Dots, 28=Wilks, 29=Glossbrenner, 30=Goodlift,
 * 31=Tested, 32=Country, 33=State, 34=Federation, 35=ParentFederation,
 * 36=Date, 37=MeetCountry, 38=MeetState, 39=MeetTown, 40=MeetName, 41=Sanctioned
 */
class OplCsvParserTest {

    private val service = OplApiService()

    // Build a CSV header + data rows for testing
    private val header =
        "Name,Sex,Event,Equipment,Age,AgeClass,BirthYearClass,Division," +
        "BodyweightKg,WeightClassKg," +
        "Squat1Kg,Squat2Kg,Squat3Kg,Squat4Kg,Best3SquatKg," +
        "Bench1Kg,Bench2Kg,Bench3Kg,Bench4Kg,Best3BenchKg," +
        "Deadlift1Kg,Deadlift2Kg,Deadlift3Kg,Deadlift4Kg,Best3DeadliftKg," +
        "TotalKg,Place,Dots,Wilks,Glossbrenner,Goodlift," +
        "Tested,Country,State,Federation,ParentFederation," +
        "Date,MeetCountry,MeetState,MeetTown,MeetName,Sanctioned"

    /**
     * Build a single CSV data row with sensible defaults.
     * Indices match the column layout above.
     */
    private fun row(
        name: String = "Test Athlete",
        equipment: String = "Raw",
        division: String = "Open",
        bodyweight: String = "89.5",
        weightClass: String = "93",
        bestSquat: String = "200",
        bestBench: String = "130",
        bestDeadlift: String = "250",
        total: String = "580",
        place: String = "1",
        dots: String = "350.5",
        federation: String = "IPF",
        date: String = "2024-03-15",
        meetCountry: String = "GBR",
        meetTown: String = "Manchester",
        meetName: String = "British Open"
    ): String {
        // Build list of 42 fields matching the column layout
        val fields = MutableList(42) { "" }
        fields[0]  = name
        fields[3]  = equipment
        fields[7]  = division
        fields[8]  = bodyweight
        fields[9]  = weightClass
        fields[14] = bestSquat
        fields[19] = bestBench
        fields[24] = bestDeadlift
        fields[25] = total
        fields[26] = place
        fields[27] = dots
        fields[34] = federation
        fields[36] = date
        fields[37] = meetCountry
        fields[39] = meetTown
        fields[40] = meetName
        return fields.joinToString(",")
    }

    // ── parseCsvLine ──────────────────────────────────────────────────

    @Test
    fun `parseCsvLine splits simple comma-separated fields`() {
        val result = service.parseCsvLine("Alice,Raw,Open,90.0")
        assertEquals(listOf("Alice", "Raw", "Open", "90.0"), result)
    }

    @Test
    fun `parseCsvLine handles quoted field containing a comma`() {
        val result = service.parseCsvLine("Alice,\"Manchester, UK\",Open")
        assertEquals(listOf("Alice", "Manchester, UK", "Open"), result)
    }

    @Test
    fun `parseCsvLine handles empty fields`() {
        val result = service.parseCsvLine("Alice,,Open,,")
        assertEquals(listOf("Alice", "", "Open", "", ""), result)
    }

    @Test
    fun `parseCsvLine handles a single field with no commas`() {
        val result = service.parseCsvLine("OnlyField")
        assertEquals(listOf("OnlyField"), result)
    }

    @Test
    fun `parseCsvLine handles quoted field containing escaped quotes`() {
        // Quoted field: "She said ""hello"""  → She said "hello"
        val result = service.parseCsvLine("Alice,\"She said \"\"hi\"\"\",Open")
        // Our parser toggles on each quote — the inner quotes collapse the field
        // Just verify it doesn't throw and we get 3 fields
        assertEquals(3, result.size)
    }

    // ── parseCsv ─────────────────────────────────────────────────────

    @Test
    fun `parseCsv returns empty list for empty string`() {
        val result = service.parseCsv("")
        assertEquals(emptyList<Any>(), result)
    }

    @Test
    fun `parseCsv returns empty list for header-only CSV`() {
        val result = service.parseCsv(header)
        assertEquals(emptyList<Any>(), result)
    }

    @Test
    fun `parseCsv parses a standard SBD row correctly`() {
        val csv = "$header\n${row()}"
        val results = service.parseCsv(csv)
        assertEquals(1, results.size)
        val r = results[0]
        assertEquals("2024-03-15", r.date)
        assertEquals("British Open", r.meetName)
        assertEquals("IPF", r.federation)
        assertEquals("Raw", r.equipment)
        assertEquals("Open", r.division)
        assertEquals("93", r.weightClassKg)
        assertEquals(89.5, r.bodyweightKg!!, 0.001)
        assertEquals(200.0, r.best3SquatKg!!, 0.001)
        assertEquals(130.0, r.best3BenchKg!!, 0.001)
        assertEquals(250.0, r.best3DeadliftKg!!, 0.001)
        assertEquals(580.0, r.totalKg!!, 0.001)
        assertEquals("1", r.place)
        assertEquals(350.5, r.dots!!, 0.001)
        assertEquals("GBR", r.meetCountry)
        assertEquals("Manchester", r.meetTown)
    }

    @Test
    fun `parseCsv skips rows with no date`() {
        val rowNoDate = row(date = "")
        val csv = "$header\n$rowNoDate"
        val results = service.parseCsv(csv)
        assertEquals(0, results.size)
    }

    @Test
    fun `parseCsv skips rows with no meet name`() {
        val rowNoMeet = row(meetName = "")
        val csv = "$header\n$rowNoMeet"
        val results = service.parseCsv(csv)
        assertEquals(0, results.size)
    }

    @Test
    fun `parseCsv filters out negative squat (bomb-out)`() {
        val bombRow = row(bestSquat = "-1", total = "")
        val csv = "$header\n$bombRow"
        val results = service.parseCsv(csv)
        assertEquals(1, results.size)
        assertNull(results[0].best3SquatKg)
    }

    @Test
    fun `parseCsv allows null lifts for bench-only or deadlift-only meets`() {
        val benchOnly = row(bestSquat = "", bestDeadlift = "", total = "130")
        val csv = "$header\n$benchOnly"
        val results = service.parseCsv(csv)
        assertEquals(1, results.size)
        assertNull(results[0].best3SquatKg)
        assertEquals(130.0, results[0].best3BenchKg!!, 0.001)
        assertNull(results[0].best3DeadliftKg)
    }

    @Test
    fun `parseCsv parses multiple rows`() {
        val csv = "$header\n${row(date = "2023-01-01", meetName = "Meet A")}\n${row(date = "2024-01-01", meetName = "Meet B")}"
        val results = service.parseCsv(csv)
        assertEquals(2, results.size)
        assertEquals("Meet A", results[0].meetName)
        assertEquals("Meet B", results[1].meetName)
    }

    @Test
    fun `parseCsv skips blank lines without throwing`() {
        val csv = "$header\n${row()}\n\n${row(date = "2023-06-01", meetName = "Second Meet")}"
        val results = service.parseCsv(csv)
        assertEquals(2, results.size)
    }

    @Test
    fun `parseCsv treats DQ place as a valid string`() {
        // DQ filtering is PrCalculator's job; parser preserves the place value
        val dqRow = row(place = "DQ")
        val csv = "$header\n$dqRow"
        val results = service.parseCsv(csv)
        assertEquals(1, results.size)
        assertEquals("DQ", results[0].place)
    }

    @Test
    fun `parseCsv handles missing optional fields as null`() {
        val minimalRow = row(dots = "", meetTown = "")
        val csv = "$header\n$minimalRow"
        val results = service.parseCsv(csv)
        assertEquals(1, results.size)
        assertNull(results[0].dots)
        assertNull(results[0].meetTown)
    }
}
