package com.gooseco.myliftsquad.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "competition_entries",
    indices = [Index(value = ["athleteSlug"])]
)
data class CompetitionEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val athleteSlug: String,
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
