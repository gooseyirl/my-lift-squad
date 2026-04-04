package com.gooseco.myliftsquad.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gooseco.myliftsquad.MyLiftSquadApp
import com.gooseco.myliftsquad.data.db.Athlete
import com.gooseco.myliftsquad.data.db.Squad
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class BackupSquad(val id: Int, val name: String, val athletes: List<Athlete>)
data class BackupData(
    val version: Int = 1,
    val squads: List<BackupSquad>,
    val orphanedFavourites: List<Athlete> = emptyList()
)

enum class BackupStatus { Idle, Success, Error }

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = (application as MyLiftSquadApp).database
    private val gson = Gson()

    private val _exportStatus = MutableStateFlow(BackupStatus.Idle)
    val exportStatus: StateFlow<BackupStatus> = _exportStatus

    private val _restoreStatus = MutableStateFlow(BackupStatus.Idle)
    val restoreStatus: StateFlow<BackupStatus> = _restoreStatus

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val squads: List<Squad> = db.squadDao().getAllSquadsList()
                val athletes: List<Athlete> = db.athleteDao().getAllAthletes()
                val backupSquads: List<BackupSquad> = squads.map { squad ->
                    BackupSquad(
                        id = squad.id,
                        name = squad.name,
                        athletes = athletes.filter { it.squadId == squad.id }
                    )
                }
                val systemSquad = db.squadDao().getSystemSquad()
                val orphanedFavourites: List<Athlete> = if (systemSquad != null) {
                    athletes.filter { it.squadId == systemSquad.id }
                } else {
                    emptyList()
                }
                val json = gson.toJson(BackupData(squads = backupSquads, orphanedFavourites = orphanedFavourites))
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray())
                }
                _exportStatus.value = BackupStatus.Success
            } catch (e: Exception) {
                _exportStatus.value = BackupStatus.Error
            }
        }
    }

    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().readText()
                } ?: throw Exception("Could not read file")

                val backup = gson.fromJson(json, BackupData::class.java)

                // Wipe existing data (competition entries first due to no FK constraint, but clean anyway)
                db.competitionEntryDao().deleteAll()
                db.athleteDao().deleteAll()
                db.squadDao().deleteAll()

                // Restore
                val squads = backup.squads.map { Squad(id = it.id, name = it.name) }
                db.squadDao().insertAll(squads)
                val athletes = backup.squads.flatMap { it.athletes }
                db.athleteDao().insertAll(athletes)

                // Restore orphaned favourites into the system squad
                if (backup.orphanedFavourites.isNotEmpty()) {
                    val systemSquad = db.squadDao().getSystemSquad()
                        ?: run {
                            val newId = db.squadDao().insert(Squad(name = "__system_favourites__", isSystem = true))
                            db.squadDao().getSystemSquad()!!
                        }
                    val orphans = backup.orphanedFavourites.map { it.copy(squadId = systemSquad.id) }
                    db.athleteDao().insertAll(orphans)
                }

                _restoreStatus.value = BackupStatus.Success
            } catch (e: Exception) {
                _restoreStatus.value = BackupStatus.Error
            }
        }
    }

    fun clearExportStatus() { _exportStatus.value = BackupStatus.Idle }
    fun clearRestoreStatus() { _restoreStatus.value = BackupStatus.Idle }
}
