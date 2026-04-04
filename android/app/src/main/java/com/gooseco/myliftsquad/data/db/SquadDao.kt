package com.gooseco.myliftsquad.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SquadDao {

    @Query("SELECT * FROM squads ORDER BY name ASC")
    fun getAllSquads(): Flow<List<Squad>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(squad: Squad): Long

    @Delete
    suspend fun delete(squad: Squad)

    @Query("SELECT * FROM squads WHERE id = :id LIMIT 1")
    suspend fun getSquadById(id: Int): Squad?

    @Query("SELECT s.id, s.name, COUNT(a.id) as athleteCount FROM squads s LEFT JOIN athletes a ON a.squadId = s.id GROUP BY s.id ORDER BY s.name ASC")
    fun getAllSquadsWithCount(): Flow<List<SquadWithCount>>
}
