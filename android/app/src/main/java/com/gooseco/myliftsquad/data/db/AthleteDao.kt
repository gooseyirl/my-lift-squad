package com.gooseco.myliftsquad.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AthleteDao {

    @Query("SELECT * FROM athletes WHERE squadId = :squadId ORDER BY name ASC")
    fun getAthletesForSquad(squadId: Int): Flow<List<Athlete>>

    @Query("""
        SELECT athletes.*, squads.name as squadName
        FROM athletes
        JOIN squads ON athletes.squadId = squads.id
        WHERE athletes.isFavourite = 1
        ORDER BY athletes.name ASC
    """)
    fun getFavouritesWithSquad(): Flow<List<AthleteWithSquad>>

    @Query("SELECT COUNT(*) FROM athletes WHERE isFavourite = 1")
    suspend fun getFavouriteCount(): Int

    @Query("SELECT COUNT(*) FROM athletes WHERE isFavourite = 1")
    fun observeFavouriteCount(): Flow<Int>

    @Query("UPDATE athletes SET isFavourite = :isFavourite WHERE id = :athleteId")
    suspend fun setFavourite(athleteId: Int, isFavourite: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(athlete: Athlete)

    @Delete
    suspend fun delete(athlete: Athlete)
}
