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

class ShareApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://myliftsquad-api.gooseyirl.workers.dev"
    private val json = "application/json; charset=utf-8".toMediaType()

    suspend fun shareSquad(name: String, athletes: List<AthleteRef>): String =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("name", name)
                put("athletes", JSONArray().apply {
                    athletes.forEach { a ->
                        put(JSONObject().apply {
                            put("name", a.name)
                            put("slug", a.slug)
                        })
                    }
                })
            }.toString().toRequestBody(json)

            val request = Request.Builder()
                .url("$baseUrl/squads")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw Exception("Empty response")
            if (!response.isSuccessful) throw Exception("Server error: ${response.code}")

            JSONObject(responseBody).getString("code")
        }

    suspend fun importSquad(code: String): SharedSquad =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/squads/${code.uppercase()}")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw Exception("Empty response")
            if (response.code == 404) throw Exception("Squad not found. Check the code and try again.")
            if (!response.isSuccessful) throw Exception("Server error: ${response.code}")

            val obj = JSONObject(responseBody)
            val name = obj.getString("name")
            val athletesArr = obj.getJSONArray("athletes")
            val athletes = (0 until athletesArr.length()).map { i ->
                val a = athletesArr.getJSONObject(i)
                AthleteRef(name = a.getString("name"), slug = a.getString("slug"))
            }
            SharedSquad(name = name, athletes = athletes)
        }
}
