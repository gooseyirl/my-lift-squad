package com.gooseco.myliftsquad

import com.gooseco.myliftsquad.data.api.OplApiService
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OplApiServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var service: OplApiService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        service = OplApiService(baseUrl = server.url("").toString().trimEnd('/'))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ── fetchCompetitionHistory ───────────────────────────────────────

    @Test
    fun `fetchCompetitionHistory requests correct path`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(minimalCsv()))
        service.fetchCompetitionHistory("john-doe")
        val request = server.takeRequest()
        assertEquals("/liftercsv/john-doe", request.path)
    }

    @Test
    fun `fetchCompetitionHistory parses results from CSV response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(minimalCsv(
            bestSquat = "200", bestBench = "130", bestDeadlift = "250", total = "580"
        )))
        val results = service.fetchCompetitionHistory("athlete-slug")
        assertEquals(1, results.size)
        assertEquals(200.0, results[0].best3SquatKg!!, 0.001)
        assertEquals(130.0, results[0].best3BenchKg!!, 0.001)
        assertEquals(250.0, results[0].best3DeadliftKg!!, 0.001)
        assertEquals(580.0, results[0].totalKg!!, 0.001)
        assertEquals("British Open", results[0].meetName)
        assertEquals("2024-03-15", results[0].date)
    }

    @Test
    fun `fetchCompetitionHistory returns empty list on 404`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val results = service.fetchCompetitionHistory("unknown-athlete")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `fetchCompetitionHistory returns empty list on 500`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))
        val results = service.fetchCompetitionHistory("athlete-slug")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `fetchCompetitionHistory returns empty list for header-only CSV`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(CSV_HEADER))
        val results = service.fetchCompetitionHistory("athlete-slug")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `fetchCompetitionHistory parses multiple rows`() = runTest {
        val csv = buildString {
            appendLine(CSV_HEADER)
            appendLine(csvRow(date = "2024-01-01", meetName = "Meet One"))
            appendLine(csvRow(date = "2023-06-01", meetName = "Meet Two"))
        }
        server.enqueue(MockResponse().setResponseCode(200).setBody(csv))
        val results = service.fetchCompetitionHistory("athlete-slug")
        assertEquals(2, results.size)
        assertEquals("Meet One", results[0].meetName)
        assertEquals("Meet Two", results[1].meetName)
    }

    @Test
    fun `fetchCompetitionHistory filters out negative lifts`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(minimalCsv(
            bestSquat = "-1", bestBench = "130", bestDeadlift = "250", total = ""
        )))
        val results = service.fetchCompetitionHistory("athlete-slug")
        assertEquals(1, results.size)
        assertNull(results[0].best3SquatKg)
        assertEquals(130.0, results[0].best3BenchKg!!, 0.001)
    }

    @Test
    fun `fetchCompetitionHistory null lifts for bench-only meet`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(minimalCsv(
            bestSquat = "", bestBench = "130", bestDeadlift = "", total = "130"
        )))
        val results = service.fetchCompetitionHistory("athlete-slug")
        assertEquals(1, results.size)
        assertNull(results[0].best3SquatKg)
        assertEquals(130.0, results[0].best3BenchKg!!, 0.001)
        assertNull(results[0].best3DeadliftKg)
    }

    @Test
    fun `fetchCompetitionHistory preserves DQ place`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(minimalCsv(place = "DQ")))
        val results = service.fetchCompetitionHistory("athlete-slug")
        assertEquals(1, results.size)
        assertEquals("DQ", results[0].place)
    }

    @Test
    fun `fetchCompetitionHistory handles optional null fields`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(minimalCsv(
            dots = "", meetTown = "", federation = ""
        )))
        val results = service.fetchCompetitionHistory("athlete-slug")
        assertEquals(1, results.size)
        assertNull(results[0].dots)
        assertNull(results[0].meetTown)
        assertNull(results[0].federation)
    }

    // ── Helpers ───────────────────────────────────────────────────────

    companion object {
        val CSV_HEADER =
            "Name,Sex,Event,Equipment,Age,AgeClass,BirthYearClass,Division," +
            "BodyweightKg,WeightClassKg," +
            "Squat1Kg,Squat2Kg,Squat3Kg,Squat4Kg,Best3SquatKg," +
            "Bench1Kg,Bench2Kg,Bench3Kg,Bench4Kg,Best3BenchKg," +
            "Deadlift1Kg,Deadlift2Kg,Deadlift3Kg,Deadlift4Kg,Best3DeadliftKg," +
            "TotalKg,Place,Dots,Wilks,Glossbrenner,Goodlift," +
            "Tested,Country,State,Federation,ParentFederation," +
            "Date,MeetCountry,MeetState,MeetTown,MeetName,Sanctioned"

        fun csvRow(
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
            val f = MutableList(42) { "" }
            f[0]  = name; f[3]  = equipment; f[7]  = division
            f[8]  = bodyweight; f[9]  = weightClass
            f[14] = bestSquat; f[19] = bestBench; f[24] = bestDeadlift
            f[25] = total; f[26] = place; f[27] = dots; f[34] = federation
            f[36] = date; f[37] = meetCountry; f[39] = meetTown; f[40] = meetName
            return f.joinToString(",")
        }

        fun minimalCsv(
            bestSquat: String = "200",
            bestBench: String = "130",
            bestDeadlift: String = "250",
            total: String = "580",
            place: String = "1",
            dots: String = "350.5",
            federation: String = "IPF",
            meetTown: String = "Manchester"
        ): String = "$CSV_HEADER\n${csvRow(
            bestSquat = bestSquat, bestBench = bestBench,
            bestDeadlift = bestDeadlift, total = total,
            place = place, dots = dots, federation = federation, meetTown = meetTown
        )}"
    }
}
