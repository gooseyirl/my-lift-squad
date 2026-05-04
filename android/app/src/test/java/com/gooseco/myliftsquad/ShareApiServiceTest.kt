package com.gooseco.myliftsquad

import com.gooseco.myliftsquad.data.api.AthleteRef
import com.gooseco.myliftsquad.data.api.ShareApiService
import com.gooseco.myliftsquad.data.api.SharedSquad
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ShareApiServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var service: ShareApiService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        service = ShareApiService(baseUrl = server.url("").toString().trimEnd('/'))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ── buildSquadJson ────────────────────────────────────────────────

    @Test
    fun `buildSquadJson produces correct name field`() {
        val json = service.buildSquadJson("My Squad", emptyList())
        assertEquals("My Squad", json.getString("name"))
    }

    @Test
    fun `buildSquadJson produces correct athletes array`() {
        val athletes = listOf(
            AthleteRef(name = "Alice Smith", slug = "alice-smith"),
            AthleteRef(name = "Bob Jones", slug = "bob-jones")
        )
        val json = service.buildSquadJson("Crew", athletes)
        val arr = json.getJSONArray("athletes")
        assertEquals(2, arr.length())
        assertEquals("Alice Smith", arr.getJSONObject(0).getString("name"))
        assertEquals("alice-smith", arr.getJSONObject(0).getString("slug"))
        assertEquals("Bob Jones", arr.getJSONObject(1).getString("name"))
        assertEquals("bob-jones", arr.getJSONObject(1).getString("slug"))
    }

    @Test
    fun `buildSquadJson with no athletes produces empty array`() {
        val json = service.buildSquadJson("Empty Squad", emptyList())
        assertEquals(0, json.getJSONArray("athletes").length())
    }

    // ── parseSharedSquad ──────────────────────────────────────────────

    @Test
    fun `parseSharedSquad reads name and athletes`() {
        val json = JSONObject("""
            {
              "name": "My Squad",
              "athletes": [
                {"name": "Alice", "slug": "alice"},
                {"name": "Bob",   "slug": "bob"}
              ]
            }
        """.trimIndent())
        val result = service.parseSharedSquad(json)
        assertEquals("My Squad", result.name)
        assertEquals(2, result.athletes.size)
        assertEquals("Alice", result.athletes[0].name)
        assertEquals("alice", result.athletes[0].slug)
        assertEquals("Bob", result.athletes[1].name)
        assertEquals("bob", result.athletes[1].slug)
    }

    @Test
    fun `parseSharedSquad handles empty athletes array`() {
        val json = JSONObject("""{"name": "Empty", "athletes": []}""")
        val result = service.parseSharedSquad(json)
        assertEquals("Empty", result.name)
        assertEquals(0, result.athletes.size)
    }

    // ── buildSquadJson / parseSharedSquad round-trip ──────────────────

    @Test
    fun `JSON round-trip preserves squad name and all athletes`() {
        val original = SharedSquad(
            name = "Powerhouse",
            athletes = listOf(
                AthleteRef("Alice Smith", "alice-smith"),
                AthleteRef("Bob Jones", "bob-jones"),
                AthleteRef("Carol Lee", "carol-lee")
            )
        )
        val json = service.buildSquadJson(original.name, original.athletes)
        val parsed = service.parseSharedSquad(json)
        assertEquals(original.name, parsed.name)
        assertEquals(original.athletes.size, parsed.athletes.size)
        original.athletes.zip(parsed.athletes).forEach { (expected, actual) ->
            assertEquals(expected.name, actual.name)
            assertEquals(expected.slug, actual.slug)
        }
    }

    // ── shareSquad (HTTP) ─────────────────────────────────────────────

    @Test
    fun `shareSquad sends POST to correct path and returns code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"code":"ABC123"}"""))

        val code = service.shareSquad("My Squad", listOf(AthleteRef("Alice", "alice")))

        assertEquals("ABC123", code)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/squads", request.path)
        assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
    }

    @Test
    fun `shareSquad request body contains squad name and athletes`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"code":"XYZ999"}"""))

        service.shareSquad("Crew", listOf(AthleteRef("Alice", "alice-slug")))

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("Crew", body.getString("name"))
        val athletes = body.getJSONArray("athletes")
        assertEquals(1, athletes.length())
        assertEquals("Alice", athletes.getJSONObject(0).getString("name"))
        assertEquals("alice-slug", athletes.getJSONObject(0).getString("slug"))
    }

    @Test(expected = Exception::class)
    fun `shareSquad throws on server error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))
        service.shareSquad("Squad", emptyList())
    }

    // ── tryImportSquad (HTTP) ─────────────────────────────────────────

    @Test
    fun `tryImportSquad returns SharedSquad on 200`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"name":"Imported Squad","athletes":[{"name":"Alice","slug":"alice"}]}
        """.trimIndent()))

        val result = service.tryImportSquad("ABC123")!!
        assertEquals("Imported Squad", result.name)
        assertEquals(1, result.athletes.size)
        assertEquals("Alice", result.athletes[0].name)
    }

    @Test
    fun `tryImportSquad returns null on 404`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = service.tryImportSquad("XXXXXX")
        assertNull(result)
    }

    @Test
    fun `tryImportSquad sends GET to correct path with uppercased code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        service.tryImportSquad("abc123")
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/squads/ABC123", request.path)
    }

    @Test(expected = Exception::class)
    fun `tryImportSquad throws on 500`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        service.tryImportSquad("ABC123")
    }

    // ── shareBundle (HTTP) ────────────────────────────────────────────

    @Test
    fun `shareBundle sends POST to correct path and returns code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"code":"BUNDLE"}"""))

        val squads = listOf(
            SharedSquad("Squad A", listOf(AthleteRef("Alice", "alice"))),
            SharedSquad("Squad B", listOf(AthleteRef("Bob", "bob")))
        )
        val code = service.shareBundle(squads)

        assertEquals("BUNDLE", code)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/bundles", request.path)
    }

    @Test
    fun `shareBundle request body contains all squads`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"code":"XY1234"}"""))

        service.shareBundle(listOf(
            SharedSquad("Squad A", listOf(AthleteRef("Alice", "alice"))),
            SharedSquad("Squad B", listOf(AthleteRef("Bob", "bob")))
        ))

        val body = JSONObject(server.takeRequest().body.readUtf8())
        val squadsArr = body.getJSONArray("squads")
        assertEquals(2, squadsArr.length())
        assertEquals("Squad A", squadsArr.getJSONObject(0).getString("name"))
        assertEquals("Squad B", squadsArr.getJSONObject(1).getString("name"))
    }

    // ── tryImportBundle (HTTP) ────────────────────────────────────────

    @Test
    fun `tryImportBundle returns SharedBundle on 200`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"squads":[
              {"name":"Squad A","athletes":[{"name":"Alice","slug":"alice"}]},
              {"name":"Squad B","athletes":[{"name":"Bob","slug":"bob"}]}
            ]}
        """.trimIndent()))

        val result = service.tryImportBundle("BUNDLE")!!
        assertEquals(2, result.squads.size)
        assertEquals("Squad A", result.squads[0].name)
        assertEquals("Squad B", result.squads[1].name)
        assertEquals("Alice", result.squads[0].athletes[0].name)
    }

    @Test
    fun `tryImportBundle returns null on 404`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = service.tryImportBundle("XXXXXX")
        assertNull(result)
    }

    @Test
    fun `tryImportBundle sends GET to correct path with uppercased code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        service.tryImportBundle("abc123")
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/bundles/ABC123", request.path)
    }
}
