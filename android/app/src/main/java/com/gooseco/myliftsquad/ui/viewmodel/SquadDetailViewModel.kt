package com.gooseco.myliftsquad.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gooseco.myliftsquad.MyLiftSquadApp
import com.gooseco.myliftsquad.data.api.OplAthlete
import com.gooseco.myliftsquad.data.api.OplApiService
import com.gooseco.myliftsquad.data.db.Athlete
import com.gooseco.myliftsquad.data.db.CompetitionEntry
import com.gooseco.myliftsquad.data.db.Squad
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SquadDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as MyLiftSquadApp).database
    private val squadDao = db.squadDao()
    private val athleteDao = db.athleteDao()
    private val competitionEntryDao = db.competitionEntryDao()
    private val apiService = OplApiService()

    private val squadIdFlow = MutableStateFlow<Int>(-1)
    private val viewingAthleteSlug = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val squad: StateFlow<Squad?> = squadIdFlow
        .flatMapLatest { id ->
            flow { emit(if (id >= 0) squadDao.getSquadById(id) else null) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val athletes: StateFlow<List<Athlete>> = squadIdFlow
        .flatMapLatest { id ->
            if (id >= 0) athleteDao.getAthletesForSquad(id)
            else flow { emit(emptyList()) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val competitionHistory: StateFlow<List<CompetitionEntry>> = viewingAthleteSlug
        .flatMapLatest { slug ->
            if (slug != null) competitionEntryDao.getEntriesForAthlete(slug)
            else flow { emit(emptyList()) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _historyLoading = MutableStateFlow(false)
    val historyLoading: StateFlow<Boolean> = _historyLoading

    private val _historyError = MutableStateFlow<String?>(null)
    val historyError: StateFlow<String?> = _historyError

    private val _refreshingAll = MutableStateFlow(false)
    val refreshingAll: StateFlow<Boolean> = _refreshingAll

    private val _maxFavouritesReached = MutableStateFlow(false)
    val maxFavouritesReached: StateFlow<Boolean> = _maxFavouritesReached

    val favouriteCount: StateFlow<Int> = athleteDao.observeFavouriteCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    fun init(id: Int) {
        squadIdFlow.value = id
    }

    /** Called when the user opens an athlete's detail sheet. Loads from cache or fetches. */
    fun viewAthlete(athlete: Athlete) {
        viewingAthleteSlug.value = athlete.slug
        viewModelScope.launch {
            val cached = competitionEntryDao.countForAthlete(athlete.slug)
            if (cached == 0) {
                fetchHistory(athlete.slug)
            }
        }
    }

    /** Called when the user dismisses the sheet. */
    fun clearViewingAthlete() {
        viewingAthleteSlug.value = null
        _historyError.value = null
    }

    /** Force re-fetch history for a single athlete. */
    fun refreshAthleteHistory(slug: String) {
        viewModelScope.launch {
            fetchHistory(slug, force = true)
        }
    }

    /** Force re-fetch history for every athlete in the squad. */
    fun refreshAllAthletes() {
        val current = athletes.value
        if (current.isEmpty()) return
        viewModelScope.launch {
            _refreshingAll.value = true
            current.forEach { athlete ->
                fetchHistory(athlete.slug, force = true)
            }
            _refreshingAll.value = false
        }
    }

    private suspend fun fetchHistory(slug: String, force: Boolean = false) {
        _historyLoading.value = true
        _historyError.value = null
        try {
            if (force) competitionEntryDao.deleteForAthlete(slug)
            val results = apiService.fetchCompetitionHistory(slug)
            if (results.isEmpty()) {
                _historyError.value = "No competition data found."
            } else {
                val entries = results.map { r ->
                    CompetitionEntry(
                        athleteSlug = slug,
                        date = r.date,
                        meetName = r.meetName,
                        federation = r.federation,
                        equipment = r.equipment,
                        division = r.division,
                        weightClassKg = r.weightClassKg,
                        bodyweightKg = r.bodyweightKg,
                        best3SquatKg = r.best3SquatKg,
                        best3BenchKg = r.best3BenchKg,
                        best3DeadliftKg = r.best3DeadliftKg,
                        totalKg = r.totalKg,
                        place = r.place,
                        dots = r.dots,
                        meetCountry = r.meetCountry,
                        meetTown = r.meetTown
                    )
                }
                competitionEntryDao.insertAll(entries)
            }
        } catch (e: Exception) {
            _historyError.value = "Failed to load competition history."
        } finally {
            _historyLoading.value = false
        }
    }

    fun toggleFavourite(athlete: Athlete) {
        viewModelScope.launch {
            if (athlete.isFavourite) {
                athleteDao.setFavourite(athlete.id, false)
            } else {
                val count = athleteDao.getFavouriteCount()
                if (count >= 3) {
                    _maxFavouritesReached.value = true
                } else {
                    athleteDao.setFavourite(athlete.id, true)
                }
            }
        }
    }

    fun dismissMaxFavouritesMessage() {
        _maxFavouritesReached.value = false
    }

    fun deleteAthlete(athlete: Athlete) {
        viewModelScope.launch {
            athleteDao.delete(athlete)
            competitionEntryDao.deleteForAthlete(athlete.slug)
        }
    }

    fun addAthlete(oplAthlete: OplAthlete) {
        val squadId = squadIdFlow.value
        if (squadId < 0) return
        viewModelScope.launch {
            athleteDao.insert(
                Athlete(
                    squadId = squadId,
                    name = oplAthlete.name,
                    slug = oplAthlete.slug,
                    country = oplAthlete.country,
                    federation = oplAthlete.federation,
                    bestSquat = oplAthlete.bestSquat,
                    bestBench = oplAthlete.bestBench,
                    bestDeadlift = oplAthlete.bestDeadlift,
                    bestTotal = oplAthlete.bestTotal,
                    weightClass = oplAthlete.weightClass,
                    equipment = oplAthlete.equipment,
                    lastCompDate = oplAthlete.lastCompDate,
                    gender = oplAthlete.gender
                )
            )
        }
    }
}
