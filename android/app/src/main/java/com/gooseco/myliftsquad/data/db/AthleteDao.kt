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

    @Query("SELECT * FROM athletes WHERE slug = :slug LIMIT 1")
    suspend fun getAthleteBySlug(slug: String): Athlete?

    @Query("SELECT slug FROM athletes")
    fun getAllSlugs(): Flow<List<String>>

    @Query("SELECT slug FROM athletes WHERE squadId = :squadId")
    fun getSlugsBySquad(squadId: Int): Flow<List<String>>

    @Query("SELECT * FROM athletes WHERE slug = :slug AND squadId = :squadId LIMIT 1")
    suspend fun getAthleteBySlugAndSquad(slug: String, squadId: Int): Athlete?

    @Query("SELECT * FROM athletes")
    suspend fun getAllAthletes(): List<Athlete>

    @Query("DELETE FROM athletes")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(athletes: List<Athlete>)

    @Query("UPDATE athletes SET squadId = :newSquadId WHERE squadId = :oldSquadId AND isFavourite = 1")
    suspend fun moveFavouritesToSquad(oldSquadId: Int, newSquadId: Int)

    @Query("""
        UPDATE athletes
        SET federation = :federation, weightClass = :weightClass, equipment = :equipment
        WHERE id = :athleteId
    """)
    suspend fun updateLastCompDetails(athleteId: Int, federation: String?, weightClass: String?, equipment: String?)

    @Query("""
        UPDATE athletes
        SET bestSquat = :bestSquat, bestBench = :bestBench, bestDeadlift = :bestDeadlift, bestTotal = :bestTotal
        WHERE id = :athleteId
    """)
    suspend fun updatePRs(athleteId: Int, bestSquat: Double?, bestBench: Double?, bestDeadlift: Double?, bestTotal: Double?)
}
