package com.gooseco.myliftsquad.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "squads")
data class Squad(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val isSystem: Boolean = false
)
