package com.gooseco.myliftsquad.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AthleteRef(val name: String, val slug: String)
data class SharedSquad(val name: String, val athletes: List<AthleteRef>)
data class SharedBundle(val squads: List<SharedSquad>)

class ShareApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://myliftsquad-api.gooseyirl.workers.dev"
    private val json = "application/json; charset=utf-8".toMediaType()

    // ── Single squad ─────────────────────────────────────────────────

    suspend fun shareSquad(name: String, athletes: List<AthleteRef>): String =
        withContext(Dispatchers.IO) {
            val body = buildSquadJson(name, athletes).toString().toRequestBody(json)
            val request = Request.Builder().url("$baseUrl/squads").post(body).build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw Exception("Empty response")
            if (!response.isSuccessful) throw Exception("Server error: ${response.code}")
            JSONObject(responseBody).getString("code")
        }

    /** Returns null if the code was not found (404), throws on other errors. */
    suspend fun tryImportSquad(code: String): SharedSquad? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/squads/${code.uppercase()}")
                .get().build()
            val response = client.newCall(request).execute()
            if (response.code == 404) return@withContext null
            val responseBody = response.body?.string() ?: throw Exception("Empty response")
            if (!response.isSuccessful) throw Exception("Server error: ${response.code}")
            parseSharedSquad(JSONObject(responseBody))
        }

    // ── Bundle (multiple squads) ──────────────────────────────────────

    suspend fun shareBundle(squads: List<SharedSquad>): String =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("squads", JSONArray().apply {
                    squads.forEach { squad ->
                        put(buildSquadJson(squad.name, squad.athletes))
                    }
                })
            }.toString().toRequestBody(json)

            val request = Request.Builder().url("$baseUrl/bundles").post(body).build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw Exception("Empty response")
            if (!response.isSuccessful) throw Exception("Server error: ${response.code}")
            JSONObject(responseBody).getString("code")
        }

    /** Returns null if the code was not found (404), throws on other errors. */
    suspend fun tryImportBundle(code: String): SharedBundle? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/bundles/${code.uppercase()}")
                .get().build()
            val response = client.newCall(request).execute()
            if (response.code == 404) return@withContext null
            val responseBody = response.body?.string() ?: throw Exception("Empty response")
            if (!response.isSuccessful) throw Exception("Server error: ${response.code}")

            val obj = JSONObject(responseBody)
            val squadsArr = obj.getJSONArray("squads")
            val squads = (0 until squadsArr.length()).map { i ->
                parseSharedSquad(squadsArr.getJSONObject(i))
            }
            SharedBundle(squads = squads)
        }

    // ── Helpers ───────────────────────────────────────────────────────

    private fun buildSquadJson(name: String, athletes: List<AthleteRef>): JSONObject =
        JSONObject().apply {
            put("name", name)
            put("athletes", JSONArray().apply {
                athletes.forEach { a ->
                    put(JSONObject().apply {
                        put("name", a.name)
                        put("slug", a.slug)
                    })
                }
            })
        }

    private fun parseSharedSquad(obj: JSONObject): SharedSquad {
        val name = obj.getString("name")
        val athletesArr = obj.getJSONArray("athletes")
        val athletes = (0 until athletesArr.length()).map { i ->
            val a = athletesArr.getJSONObject(i)
            AthleteRef(name = a.getString("name"), slug = a.getString("slug"))
        }
        return SharedSquad(name = name, athletes = athletes)
    }
}
