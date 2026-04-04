package com.gooseco.myliftsquad.data.api

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class OplApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://www.openpowerlifting.org/api"

    /**
     * Search both men and women paths in parallel, combine and deduplicate by slug.
     */
    suspend fun searchAthletes(query: String): List<OplAthlete> = coroutineScope {
        val menDeferred = async { searchPath("men", query) }
        val womenDeferred = async { searchPath("women", query) }

        val men = menDeferred.await()
        val women = womenDeferred.await()

        // Combine and deduplicate by slug
        val combined = LinkedHashMap<String, OplAthlete>()
        for (athlete in men + women) {
            combined.putIfAbsent(athlete.slug, athlete)
        }
        combined.values.toList()
    }

    /**
     * Fetch full competition history for an athlete by slug.
     * Uses /api/liftercsv/{slug} which returns CSV with all entries sorted newest first.
     */
    suspend fun fetchCompetitionHistory(slug: String): List<CompetitionResult> =
        withContext(Dispatchers.IO) {
            try {
                val csv = get("$baseUrl/liftercsv/$slug") ?: return@withContext emptyList()
                parseCsv(csv)
            } catch (e: Exception) {
                emptyList()
            }
        }

    /**
     * CSV columns (0-indexed):
     * 0=Name, 1=Sex, 2=Event, 3=Equipment, 4=Age, 5=AgeClass, 6=BirthYearClass,
     * 7=Division, 8=BodyweightKg, 9=WeightClassKg,
     * 10-13=Squat1-4Kg, 14=Best3SquatKg,
     * 15-18=Bench1-4Kg, 19=Best3BenchKg,
     * 20-23=Deadlift1-4Kg, 24=Best3DeadliftKg,
     * 25=TotalKg, 26=Place, 27=Dots, 28=Wilks, 29=Glossbrenner, 30=Goodlift,
     * 31=Tested, 32=Country, 33=State, 34=Federation, 35=ParentFederation,
     * 36=Date, 37=MeetCountry, 38=MeetState, 39=MeetTown, 40=MeetName, 41=Sanctioned
     */
    private fun parseCsv(csv: String): List<CompetitionResult> {
        val lines = csv.lines()
        if (lines.size < 2) return emptyList()
        return lines.drop(1)
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                try {
                    val f = parseCsvLine(line)
                    val date = f.getOrNull(36)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    val meetName = f.getOrNull(40)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    CompetitionResult(
                        date = date,
                        meetName = meetName,
                        federation = f.getOrNull(34)?.takeIf { it.isNotEmpty() },
                        equipment = f.getOrNull(3)?.takeIf { it.isNotEmpty() },
                        division = f.getOrNull(7)?.takeIf { it.isNotEmpty() },
                        weightClassKg = f.getOrNull(9)?.takeIf { it.isNotEmpty() },
                        bodyweightKg = f.getOrNull(8)?.toDoubleOrNull(),
                        best3SquatKg = f.getOrNull(14)?.toDoubleOrNull()?.takeIf { it > 0 },
                        best3BenchKg = f.getOrNull(19)?.toDoubleOrNull()?.takeIf { it > 0 },
                        best3DeadliftKg = f.getOrNull(24)?.toDoubleOrNull()?.takeIf { it > 0 },
                        totalKg = f.getOrNull(25)?.toDoubleOrNull()?.takeIf { it > 0 },
                        place = f.getOrNull(26)?.takeIf { it.isNotEmpty() },
                        dots = f.getOrNull(27)?.toDoubleOrNull(),
                        meetCountry = f.getOrNull(37)?.takeIf { it.isNotEmpty() },
                        meetTown = f.getOrNull(39)?.takeIf { it.isNotEmpty() }
                    )
                } catch (e: Exception) {
                    null
                }
            }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    result.add(field.toString())
                    field.clear()
                }
                else -> field.append(ch)
            }
        }
        result.add(field.toString())
        return result
    }

    /**
     * Search a specific gender path.
     * Step 1: search/rankings to get next_index
     * Step 2: fetch rows from rankings using that index
     */
    private suspend fun searchPath(path: String, query: String): List<OplAthlete> =
        withContext(Dispatchers.IO) {
            try {
                // Step 1: search to get the start index
                val searchUrl = "$baseUrl/search/rankings/$path" +
                    "?q=${encode(query)}&start=0&lang=en&units=kg"
                val searchResponse = get(searchUrl) ?: return@withContext emptyList()
                val searchJson = JsonParser.parseString(searchResponse).asJsonObject
                val nextIndex = searchJson.get("next_index")?.asInt ?: return@withContext emptyList()

                // Step 2: fetch rows around that index
                val end = nextIndex + 24
                val rowsUrl = "$baseUrl/rankings/$path" +
                    "?start=$nextIndex&end=$end&lang=en&units=kg"
                val rowsResponse = get(rowsUrl) ?: return@withContext emptyList()
                val rowsJson = JsonParser.parseString(rowsResponse).asJsonObject
                val rows = rowsJson.getAsJsonArray("rows") ?: return@withContext emptyList()

                parseRows(rows)
            } catch (e: Exception) {
                emptyList()
            }
        }

    /**
     * Parse row arrays.
     * Row format: [idx, rank, name, slug, instagram, unknown, country, state,
     *              federation, date, country2, state2, meetPath, gender, equipment,
     *              ageClass, division, bodyweight, weightClass, squat, bench,
     *              deadlift, total, score]
     */
    private fun parseRows(rows: JsonArray): List<OplAthlete> {
        val result = mutableListOf<OplAthlete>()
        for (rowElement in rows) {
            try {
                val row = rowElement.asJsonArray
                val name        = row.getStringOrNull(2) ?: continue
                val slug        = row.getStringOrNull(3) ?: continue
                val country     = row.getStringOrNull(6)
                val federation  = row.getStringOrNull(8)
                val date        = row.getStringOrNull(9)
                val gender      = row.getStringOrNull(13)
                val equipment   = row.getStringOrNull(14)
                val weightClass = row.getStringOrNull(18)
                val squat       = row.getDoubleOrNull(19)
                val bench       = row.getDoubleOrNull(20)
                val deadlift    = row.getDoubleOrNull(21)
                val total       = row.getDoubleOrNull(22)

                result.add(
                    OplAthlete(
                        name = name,
                        slug = slug,
                        country = country,
                        federation = federation,
                        bestSquat = squat,
                        bestBench = bench,
                        bestDeadlift = deadlift,
                        bestTotal = total,
                        weightClass = weightClass,
                        equipment = equipment,
                        lastCompDate = date,
                        gender = gender
                    )
                )
            } catch (e: Exception) {
                // Skip malformed rows
            }
        }
        return result
    }

    private fun JsonArray.getStringOrNull(index: Int): String? {
        val element = this[index] ?: return null
        return if (element.isJsonNull) null else element.asString.takeIf { it.isNotEmpty() }
    }

    private fun JsonArray.getDoubleOrNull(index: Int): Double? {
        val element = this[index] ?: return null
        return if (element.isJsonNull) null
        else try { element.asDouble.takeIf { it != 0.0 } } catch (e: Exception) { null }
    }

    private fun get(url: String): String? {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private fun encode(query: String): String =
        java.net.URLEncoder.encode(query, "UTF-8")
}
