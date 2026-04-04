package com.gooseco.myliftsquad.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gooseco.myliftsquad.data.db.Athlete
import com.gooseco.myliftsquad.data.db.CompetitionEntry
import com.gooseco.myliftsquad.ui.viewmodel.SquadDetailViewModel

private fun formatKg(value: Double): String =
    if (value % 1.0 == 0.0) "${value.toInt()} kg" else "${"%.1f".format(value)} kg"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquadDetailScreen(
    squadId: Int,
    onBack: () -> Unit,
    onAddAthlete: () -> Unit,
    viewModel: SquadDetailViewModel = viewModel()
) {
    LaunchedEffect(squadId) {
        viewModel.init(squadId)
    }

    val squad by viewModel.squad.collectAsState()
    val athletes by viewModel.athletes.collectAsState()
    val refreshingAll by viewModel.refreshingAll.collectAsState()
    val competitionHistory by viewModel.competitionHistory.collectAsState()
    val historyLoading by viewModel.historyLoading.collectAsState()
    val historyError by viewModel.historyError.collectAsState()
    val maxFavouritesReached by viewModel.maxFavouritesReached.collectAsState()
    val favouriteCount by viewModel.favouriteCount.collectAsState()

    var athleteOptions by remember { mutableStateOf<Athlete?>(null) }
    var athleteToDelete by remember { mutableStateOf<Athlete?>(null) }
    var selectedAthlete by remember { mutableStateOf<Athlete?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(maxFavouritesReached) {
        if (maxFavouritesReached) {
            snackbarHostState.showSnackbar("Maximum 3 favourites allowed.")
            viewModel.dismissMaxFavouritesMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = squad?.name ?: "Squad",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (refreshingAll) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 12.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else if (athletes.isNotEmpty()) {
                        IconButton(onClick = { viewModel.refreshAllAthletes() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh all athletes",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddAthlete,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = "Add athlete",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { innerPadding ->
        if (athletes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No athletes yet. Tap + to add your first athlete.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(athletes, key = { it.id }) { athlete ->
                    AthleteCard(
                        athlete = athlete,
                        onClick = {
                            selectedAthlete = athlete
                            viewModel.viewAthlete(athlete)
                        },
                        onLongPress = { athleteOptions = athlete }
                    )
                }
            }
        }
    }

    // Options dialog on long-press
    athleteOptions?.let { athlete ->
        val canFavourite = !athlete.isFavourite && favouriteCount < 3
        AthleteOptionsDialog(
            athlete = athlete,
            canFavourite = canFavourite,
            onFavourite = { viewModel.toggleFavourite(athlete) },
            onUnfavourite = { viewModel.toggleFavourite(athlete) },
            onRemove = { athleteToDelete = athlete },
            onDismiss = { athleteOptions = null }
        )
    }

    // Remove confirmation dialog
    athleteToDelete?.let { athlete ->
        AlertDialog(
            onDismissRequest = { athleteToDelete = null },
            title = { Text("Remove athlete") },
            text = { Text("Remove ${athlete.name} from this squad?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAthlete(athlete)
                    athleteToDelete = null
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { athleteToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Athlete detail sheet
    selectedAthlete?.let { athlete ->
        ModalBottomSheet(
            onDismissRequest = {
                selectedAthlete = null
                viewModel.clearViewingAthlete()
            },
            sheetState = sheetState
        ) {
            AthleteDetailSheet(
                athlete = athlete,
                history = competitionHistory,
                historyLoading = historyLoading,
                historyError = historyError,
                onRefresh = { viewModel.refreshAthleteHistory(athlete.slug) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AthleteCard(
    athlete: Athlete,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = athlete.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val metaParts = listOfNotNull(
                        athlete.federation,
                        athlete.gender,
                        athlete.weightClass,
                        athlete.equipment
                    )
                    if (metaParts.isNotEmpty()) {
                        val primary = MaterialTheme.colorScheme.primary
                        val secondary = MaterialTheme.colorScheme.onSurfaceVariant
                        Text(
                            text = buildAnnotatedString {
                                metaParts.forEachIndexed { index, part ->
                                    if (index > 0) withStyle(SpanStyle(color = secondary)) { append(" - ") }
                                    withStyle(SpanStyle(color = if (index == 0) primary else secondary)) { append(part) }
                                }
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    val sbdParts = listOfNotNull(
                        athlete.bestSquat?.let { Pair("S", formatKg(it)) },
                        athlete.bestBench?.let { Pair("B", formatKg(it)) },
                        athlete.bestDeadlift?.let { Pair("D", formatKg(it)) }
                    )
                    if (sbdParts.isNotEmpty()) {
                        val primary = MaterialTheme.colorScheme.primary
                        val secondary = MaterialTheme.colorScheme.onSurfaceVariant
                        Text(
                            text = buildAnnotatedString {
                                sbdParts.forEachIndexed { index, (letter, value) ->
                                    if (index > 0) withStyle(SpanStyle(color = secondary)) { append("  ") }
                                    withStyle(SpanStyle(color = primary)) { append(letter) }
                                    withStyle(SpanStyle(color = secondary)) { append(" $value") }
                                }
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (athlete.isFavourite) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Favourite",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    athlete.bestTotal?.let { total ->
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Text(
                                text = formatKg(total),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AthleteOptionsDialog(
    athlete: Athlete,
    canFavourite: Boolean,
    onFavourite: () -> Unit,
    onUnfavourite: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(athlete.name) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (canFavourite) {
                    TextButton(
                        onClick = { onFavourite(); onDismiss() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Favourite",
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                if (athlete.isFavourite) {
                    TextButton(
                        onClick = { onUnfavourite(); onDismiss() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Unfavourite",
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                TextButton(
                    onClick = { onRemove(); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Remove",
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
internal fun AthleteDetailSheet(
    athlete: Athlete,
    history: List<CompetitionEntry>,
    historyLoading: Boolean,
    historyError: String?,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = athlete.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val subtitle = listOfNotNull(athlete.federation, athlete.country)
                            .joinToString(" · ")
                        if (subtitle.isNotEmpty()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        val meta = listOfNotNull(
                            athlete.gender?.let { if (it == "M") "Male" else "Female" },
                            athlete.weightClass?.let { "$it kg" },
                            athlete.equipment
                        ).joinToString(" · ")
                        if (meta.isNotEmpty()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onRefresh,
                        enabled = !historyLoading
                    ) {
                        if (historyLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh competition history",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Best lifts",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    LiftStat(label = "Squat", value = athlete.bestSquat)
                    LiftStat(label = "Bench", value = athlete.bestBench)
                    LiftStat(label = "Deadlift", value = athlete.bestDeadlift)
                    LiftStat(label = "Total", value = athlete.bestTotal, highlight = true)
                }

                athlete.lastCompDate?.let { date ->
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Last competition",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = date,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Competition history",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        when {
            historyLoading && history.isEmpty() -> item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            historyError != null && history.isEmpty() -> item {
                Text(
                    text = historyError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
            history.isEmpty() -> item {
                Text(
                    text = "No competitions found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
            else -> items(history) { entry ->
                CompetitionEntryRow(entry = entry)
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

@Composable
private fun CompetitionEntryRow(entry: CompetitionEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.meetName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val location = listOfNotNull(entry.meetTown, entry.meetCountry).joinToString(", ")
                val subtitle = listOfNotNull(
                    entry.federation,
                    location.takeIf { it.isNotEmpty() }
                ).joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                val meta = listOfNotNull(entry.equipment, entry.division).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = entry.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                entry.place?.let { place ->
                    Text(
                        text = "Place: $place",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            LiftStat(label = "SQ", value = entry.best3SquatKg)
            LiftStat(label = "BP", value = entry.best3BenchKg)
            LiftStat(label = "DL", value = entry.best3DeadliftKg)
            LiftStat(label = "Total", value = entry.totalKg, highlight = true)
        }
    }
}

@Composable
private fun LiftStat(label: String, value: Double?, highlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (value != null) formatKg(value) else "—",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoChip(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(0.dp)
    )
}
