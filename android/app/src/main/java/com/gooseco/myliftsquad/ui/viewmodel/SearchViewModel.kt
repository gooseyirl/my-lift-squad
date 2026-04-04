package com.gooseco.myliftsquad.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gooseco.myliftsquad.data.api.OplAthlete
import com.gooseco.myliftsquad.data.api.OplApiService
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val apiService = OplApiService()

    val searchQuery = MutableStateFlow("")

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    @OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<OplAthlete>> = searchQuery
        .debounce(400L)
        .distinctUntilChanged()
        .filter { it.length >= 2 }
        .flatMapLatest { query ->
            flow {
                _isLoading.value = true
                _error.value = null
                try {
                    val results = apiService.searchAthletes(query)
                    emit(results)
                    _error.value = null
                } catch (e: Exception) {
                    emit(emptyList())
                    _error.value = "Search failed: ${e.message}"
                } finally {
                    _isLoading.value = false
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )
}
