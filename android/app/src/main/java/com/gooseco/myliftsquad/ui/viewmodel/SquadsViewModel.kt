package com.gooseco.myliftsquad.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gooseco.myliftsquad.MyLiftSquadApp
import com.gooseco.myliftsquad.data.db.Athlete
import com.gooseco.myliftsquad.data.db.AthleteWithSquad
import com.gooseco.myliftsquad.data.db.Squad
import com.gooseco.myliftsquad.data.db.SquadWithCount
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SquadsViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as MyLiftSquadApp).database
    private val squadDao = db.squadDao()
    private val athleteDao = db.athleteDao()

    val squads: StateFlow<List<SquadWithCount>> = squadDao.getAllSquadsWithCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val favourites: StateFlow<List<AthleteWithSquad>> = athleteDao.getFavouritesWithSquad()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _nameError = MutableStateFlow<String?>(null)
    val nameError: StateFlow<String?> = _nameError.asStateFlow()

    private val _squadCreated = MutableSharedFlow<Unit>(replay = 0)
    val squadCreated: SharedFlow<Unit> = _squadCreated.asSharedFlow()

    fun createSquad(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            if (squadDao.countByName(trimmed) > 0) {
                _nameError.value = "A squad with this name already exists"
            } else {
                squadDao.insert(Squad(name = trimmed))
                _nameError.value = null
                _squadCreated.emit(Unit)
            }
        }
    }

    fun clearNameError() {
        _nameError.value = null
    }

    fun unfavourite(athlete: Athlete) {
        viewModelScope.launch {
            athleteDao.setFavourite(athlete.id, false)
        }
    }

    fun deleteSquad(squad: SquadWithCount) {
        viewModelScope.launch {
            val systemSquad = squadDao.getSystemSquad()
            if (systemSquad != null) {
                athleteDao.moveFavouritesToSquad(squad.id, systemSquad.id)
            }
            squadDao.delete(Squad(id = squad.id, name = squad.name))
        }
    }
}
