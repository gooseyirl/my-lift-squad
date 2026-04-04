package com.gooseco.myliftsquad.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CompetitionEntryDao {

    @Query("SELECT * FROM competition_entries WHERE athleteSlug = :slug ORDER BY date DESC")
    fun getEntriesForAthlete(slug: String): Flow<List<CompetitionEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<CompetitionEntry>)

    @Query("DELETE FROM competition_entries WHERE athleteSlug = :slug")
    suspend fun deleteForAthlete(slug: String)

    @Query("SELECT COUNT(*) FROM competition_entries WHERE athleteSlug = :slug")
    suspend fun countForAthlete(slug: String): Int
}
