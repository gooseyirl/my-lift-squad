package com.gooseco.myliftsquad.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gooseco.myliftsquad.MyLiftSquadApp
import com.gooseco.myliftsquad.data.db.Athlete
import com.gooseco.myliftsquad.data.db.AthleteWithSquad
import com.gooseco.myliftsquad.data.db.Squad
import com.gooseco.myliftsquad.data.db.SquadWithCount
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    fun createSquad(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            squadDao.insert(Squad(name = trimmed))
        }
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
