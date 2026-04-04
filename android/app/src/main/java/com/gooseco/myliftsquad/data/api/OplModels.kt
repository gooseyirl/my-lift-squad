package com.gooseco.myliftsquad.data.api

data class OplAthlete(
    val name: String,
    val slug: String,
    val country: String?,
    val federation: String?,
    val bestSquat: Double?,
    val bestBench: Double?,
    val bestDeadlift: Double?,
    val bestTotal: Double?,
    val weightClass: String?,
    val equipment: String?,
    val lastCompDate: String?,
    val gender: String?
)

data class CompetitionResult(
    val date: String,
    val meetName: String,
    val federation: String?,
    val equipment: String?,
    val division: String?,
    val weightClassKg: String?,
    val bodyweightKg: Double?,
    val best3SquatKg: Double?,
    val best3BenchKg: Double?,
    val best3DeadliftKg: Double?,
    val totalKg: Double?,
    val place: String?,
    val dots: Double?,
    val meetCountry: String?,
    val meetTown: String?
)
