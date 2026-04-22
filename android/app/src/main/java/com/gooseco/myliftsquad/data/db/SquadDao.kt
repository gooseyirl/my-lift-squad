package com.gooseco.myliftsquad.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SquadDao {

    @Query("SELECT * FROM squads WHERE isSystem = 0 ORDER BY name ASC")
    fun getAllSquads(): Flow<List<Squad>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(squad: Squad): Long

    @Delete
    suspend fun delete(squad: Squad)

    @Query("SELECT * FROM squads WHERE id = :id LIMIT 1")
    suspend fun getSquadById(id: Int): Squad?

    @Query("SELECT s.id, s.name, COUNT(a.id) as athleteCount FROM squads s LEFT JOIN athletes a ON a.squadId = s.id WHERE s.isSystem = 0 GROUP BY s.id ORDER BY s.name ASC")
    fun getAllSquadsWithCount(): Flow<List<SquadWithCount>>

    @Query("SELECT * FROM squads WHERE isSystem = 0 ORDER BY name ASC")
    suspend fun getAllSquadsList(): List<Squad>

    @Query("SELECT COUNT(*) FROM squads WHERE LOWER(name) = LOWER(:name) AND isSystem = 0")
    suspend fun countByName(name: String): Int

    @Query("SELECT * FROM squads WHERE isSystem = 1 LIMIT 1")
    suspend fun getSystemSquad(): Squad?

    @Query("DELETE FROM squads")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(squads: List<Squad>)
}
