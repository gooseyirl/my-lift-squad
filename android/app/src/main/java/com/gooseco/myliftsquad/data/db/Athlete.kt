package com.gooseco.myliftsquad.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "athletes",
    foreignKeys = [
        ForeignKey(
            entity = Squad::class,
            parentColumns = ["id"],
            childColumns = ["squadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["squadId"])]
)
data class Athlete(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val squadId: Int,
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
    val gender: String?,
    val isFavourite: Boolean = false
)
