package com.gooseco.myliftsquad.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gooseco.myliftsquad.data.api.OplAthlete
import com.gooseco.myliftsquad.data.api.OplApiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val apiService = OplApiService()

    val searchQuery = MutableStateFlow("")

    private val _results = MutableStateFlow<List<OplAthlete>>(emptyList())
    val searchResults: StateFlow<List<OplAthlete>> = _results

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private val _canLoadMore = MutableStateFlow(false)
    val canLoadMore: StateFlow<Boolean> = _canLoadMore

    private val _showNoMoreResults = MutableStateFlow(false)
    val showNoMoreResults: StateFlow<Boolean> = _showNoMoreResults

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Pagination cursors — null means that gender path is exhausted
    private var nextMenStart: Int? = 0
    private var nextWomenStart: Int? = 0

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            searchQuery
                .debounce(400L)
                .distinctUntilChanged()
                .collect { query ->
                    searchJob?.cancel()
                    if (query.length < 2) {
                        _results.value = emptyList()
                        _canLoadMore.value = false
                        _error.value = null
                        return@collect
                    }
                    searchJob = launch { performSearch(query) }
                }
        }
    }

    private suspend fun performSearch(query: String) {
        nextMenStart = 0
        nextWomenStart = 0
        _isLoading.value = true
        _error.value = null
        _canLoadMore.value = false
        try {
            val page = apiService.searchAthletes(query, nextMenStart, nextWomenStart)
            _results.value = page.athletes
            nextMenStart = page.nextMenStart
            nextWomenStart = page.nextWomenStart
            _canLoadMore.value = page.hasMore
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _error.value = "Search failed: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    fun loadMore() {
        val query = searchQuery.value
        if (query.length < 2 || _isLoadingMore.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val page = apiService.searchAthletes(query, nextMenStart, nextWomenStart)
                _results.value = (_results.value + page.athletes).distinctBy { it.slug }
                nextMenStart = page.nextMenStart
                nextWomenStart = page.nextWomenStart
                _canLoadMore.value = page.hasMore
                if (!page.hasMore) {
                    _showNoMoreResults.value = true
                }
            } catch (e: Exception) {
                _error.value = "Failed to load more: ${e.message}"
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun dismissNoMoreResults() {
        _showNoMoreResults.value = false
    }
}
