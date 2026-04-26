package com.gooseco.myliftsquad.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gooseco.myliftsquad.MyLiftSquadApp
import com.gooseco.myliftsquad.data.PrCalculator
import com.gooseco.myliftsquad.data.api.OplApiService
import com.gooseco.myliftsquad.data.api.ShareApiService
import com.gooseco.myliftsquad.data.db.Athlete
import com.gooseco.myliftsquad.data.db.AthleteWithSquad
import com.gooseco.myliftsquad.data.db.CompetitionEntry
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
    private val competitionEntryDao = db.competitionEntryDao()
    private val shareApiService = ShareApiService()
    private val apiService = OplApiService()

    private val _importLoading = MutableStateFlow(false)
    val importLoading: StateFlow<Boolean> = _importLoading.asStateFlow()

    private val _importProgress = MutableStateFlow<String?>(null)
    val importProgress: StateFlow<String?> = _importProgress.asStateFlow()

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()

    private val _importedSquadName = MutableSharedFlow<String>(replay = 0)
    val importedSquadName: SharedFlow<String> = _importedSquadName.asSharedFlow()

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

    private val _renameError = MutableStateFlow<String?>(null)
    val renameError: StateFlow<String?> = _renameError.asStateFlow()

    private val _renameSuccess = MutableSharedFlow<Unit>(replay = 0)
    val renameSuccess: SharedFlow<Unit> = _renameSuccess.asSharedFlow()

    private val _squadCreated = MutableSharedFlow<Int>(replay = 0)
    val squadCreated: SharedFlow<Int> = _squadCreated.asSharedFlow()

    fun createSquad(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            if (squadDao.countByName(trimmed) > 0) {
                _nameError.value = "A squad with this name already exists"
            } else {
                val id = squadDao.insert(Squad(name = trimmed)).toInt()
                _nameError.value = null
                _squadCreated.emit(id)
            }
        }
    }

    fun clearNameError() {
        _nameError.value = null
    }

    fun renameSquad(squadId: Int, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            if (squadDao.countByName(trimmed) > 0) {
                _renameError.value = "A squad with this name already exists"
            } else {
                squadDao.rename(squadId, trimmed)
                _renameError.value = null
                _renameSuccess.emit(Unit)
            }
        }
    }

    fun clearRenameError() {
        _renameError.value = null
    }

    fun importSquad(code: String) {
        val trimmed = code.trim().uppercase()
        if (trimmed.length != 6) {
            _importError.value = "Enter a 6-character code"
            return
        }
        viewModelScope.launch {
            _importLoading.value = true
            _importError.value = null
            _importProgress.value = "Fetching squad..."
            try {
                val shared = shareApiService.importSquad(trimmed)

                // Check for duplicate name
                if (squadDao.countByName(shared.name) > 0) {
                    _importError.value = "You already have a squad named \"${shared.name}\""
                    return@launch
                }

                val squad = Squad(name = shared.name)
                val squadId = squadDao.insert(squad).toInt()
                val total = shared.athletes.size

                shared.athletes.forEachIndexed { index, ref ->
                    _importProgress.value = "Fetching data for ${ref.name} (${index + 1} of $total)..."
                    athleteDao.insert(
                        Athlete(
                            squadId = squadId,
                            name = ref.name,
                            slug = ref.slug,
                            country = null,
                            federation = null,
                            bestSquat = null,
                            bestBench = null,
                            bestDeadlift = null,
                            bestTotal = null,
                            weightClass = null,
                            equipment = null,
                            lastCompDate = null,
                            gender = null
                        )
                    )
                    val athleteId = athleteDao.getAthleteBySlugAndSquad(ref.slug, squadId)?.id
                    try {
                        val results = apiService.fetchCompetitionHistory(ref.slug)
                        if (results.isNotEmpty() && athleteId != null) {
                            val entries = results.map { r ->
                                CompetitionEntry(
                                    athleteSlug = ref.slug,
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
                            val latest = competitionEntryDao.getLatestEntry(ref.slug)
                            if (latest != null) {
                                athleteDao.updateLastCompDetails(
                                    athleteId = athleteId,
                                    federation = latest.federation,
                                    weightClass = latest.weightClassKg,
                                    equipment = latest.equipment
                                )
                            }
                            val prs = PrCalculator.calculate(entries)
                            athleteDao.updatePRs(
                                athleteId = athleteId,
                                bestSquat = prs.bestSquat,
                                bestBench = prs.bestBench,
                                bestDeadlift = prs.bestDeadlift,
                                bestTotal = prs.bestTotal
                            )
                        }
                    } catch (_: Exception) {
                        // History fetch failing for one athlete shouldn't abort the whole import
                    }
                }

                _importedSquadName.emit(shared.name)
            } catch (e: Exception) {
                _importError.value = e.message ?: "Failed to import squad."
            } finally {
                _importLoading.value = false
                _importProgress.value = null
            }
        }
    }

    fun clearImportError() {
        _importError.value = null
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
