package com.gooseco.myliftsquad.data.db

import androidx.room.Embedded

data class AthleteWithSquad(
    @Embedded val athlete: Athlete,
    val squadName: String
)
