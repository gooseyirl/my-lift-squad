package com.gooseco.myliftsquad.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gooseco.myliftsquad.R
import com.gooseco.myliftsquad.data.db.Athlete
import com.gooseco.myliftsquad.data.db.AthleteWithSquad
import com.gooseco.myliftsquad.data.db.SquadWithCount
import com.gooseco.myliftsquad.ui.viewmodel.SquadDetailViewModel
import com.gooseco.myliftsquad.ui.viewmodel.SquadsViewModel

private fun formatKgHome(value: Double): String =
    if (value % 1.0 == 0.0) "${value.toInt()} kg" else "${"%.1f".format(value)} kg"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquadsScreen(
    onSquadClick: (Int) -> Unit,
    isDonated: Boolean,
    quote: String,
    onSupportDeveloper: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: SquadsViewModel = viewModel()
) {
    val detailViewModel: SquadDetailViewModel = viewModel()
    val competitionHistory by detailViewModel.competitionHistory.collectAsState()
    val historyLoading by detailViewModel.historyLoading.collectAsState()
    val historyError by detailViewModel.historyError.collectAsState()
    var selectedFavourite by remember { mutableStateOf<Athlete?>(null) }
    val favouriteSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    val squads by viewModel.squads.collectAsState()
    val favourites by viewModel.favourites.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val nameError by viewModel.nameError.collectAsState()
    val renameError by viewModel.renameError.collectAsState()
    val importLoading by viewModel.importLoading.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()
    val importError by viewModel.importError.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var squadToDelete by remember { mutableStateOf<SquadWithCount?>(null) }
    var squadToRename by remember { mutableStateOf<SquadWithCount?>(null) }
    var squadOptions by remember { mutableStateOf<SquadWithCount?>(null) }
    var athleteToUnfavourite by remember { mutableStateOf<Athlete?>(null) }
    var fabExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.squadCreated.collect {
            showCreateDialog = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.renameSuccess.collect {
            squadToRename = null
        }
    }

    LaunchedEffect(Unit) {
        viewModel.importedSquadName.collect { name ->
            showImportDialog = false
            snackbarHostState.showSnackbar("\"$name\" imported successfully")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "My Lift Squad",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Keep track of your squads",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        Image(
                            painter = painterResource(id = R.drawable.app_icon),
                            contentDescription = "My Lift Squad",
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { innerPadding ->
            if (squads.isEmpty() && favourites.isEmpty()) {
                EmptySquadsContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    onCreateSquad = { showCreateDialog = true }
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (favourites.isNotEmpty()) {
                        item {
                            Text(
                                text = "Favourites",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        items(favourites, key = { "fav_${it.athlete.id}" }) { athleteWithSquad ->
                            FavouriteAthleteCard(
                                athleteWithSquad = athleteWithSquad,
                                onClick = {
                                    selectedFavourite = athleteWithSquad.athlete
                                    detailViewModel.viewAthlete(athleteWithSquad.athlete)
                                },
                                onLongPress = { athleteToUnfavourite = athleteWithSquad.athlete }
                            )
                        }
                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Squads",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                    items(squads, key = { it.id }) { squad ->
                        SquadCard(
                            squad = squad,
                            onClick = { onSquadClick(squad.id) },
                            onLongPress = { squadOptions = squad }
                        )
                    }
                }
            }
        }

        // Scrim — dismisses the speed dial when tapping outside
        if (fabExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { fabExpanded = false }
            )
        }

        // Quote footer (shown when donated)
        if (isDonated) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, bottom = 16.dp, end = 80.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = quote,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 14.sp
                )
            }
        }

        // Speed dial FAB
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedVisibility(
                visible = fabExpanded,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SpeedDialItem(
                        label = "New Squad",
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    ) {
                        fabExpanded = false
                        showCreateDialog = true
                    }
                    SpeedDialItem(
                        label = "Import Squad",
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    ) {
                        fabExpanded = false
                        showImportDialog = true
                    }
                    SpeedDialItem(
                        label = "Settings",
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    ) {
                        fabExpanded = false
                        onNavigateToSettings()
                    }
                    if (!isDonated) {
                        SpeedDialItem(
                            label = "Support Developer",
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        ) {
                            fabExpanded = false
                            onSupportDeveloper()
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { fabExpanded = !fabExpanded },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = if (fabExpanded) Icons.Filled.Close else Icons.Filled.Menu,
                    contentDescription = if (fabExpanded) "Close menu" else "Open menu",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateSquadDialog(
            errorMessage = nameError,
            onConfirm = { name -> viewModel.createSquad(name) },
            onDismiss = {
                showCreateDialog = false
                viewModel.clearNameError()
            }
        )
    }

    if (showImportDialog) {
        ImportSquadDialog(
            loading = importLoading,
            progress = importProgress,
            errorMessage = importError,
            onConfirm = { code -> viewModel.importSquad(code) },
            onDismiss = {
                showImportDialog = false
                viewModel.clearImportError()
            }
        )
    }

    squadOptions?.let { squad ->
        AlertDialog(
            onDismissRequest = { squadOptions = null },
            title = { Text(squad.name) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = {
                            squadOptions = null
                            squadToRename = squad
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Rename",
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    TextButton(
                        onClick = {
                            squadOptions = null
                            squadToDelete = squad
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Delete",
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { squadOptions = null }) { Text("Cancel") }
            }
        )
    }

    squadToDelete?.let { squad ->
        AlertDialog(
            onDismissRequest = { squadToDelete = null },
            title = { Text("Delete squad") },
            text = { Text("Delete \"${squad.name}\"? This will also remove all athletes in the squad.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSquad(squad)
                    squadToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { squadToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    squadToRename?.let { squad ->
        RenameSquadDialog(
            currentName = squad.name,
            errorMessage = renameError,
            onConfirm = { newName -> viewModel.renameSquad(squad.id, newName) },
            onDismiss = {
                squadToRename = null
                viewModel.clearRenameError()
            }
        )
    }

    athleteToUnfavourite?.let { athlete ->
        AlertDialog(
            onDismissRequest = { athleteToUnfavourite = null },
            title = { Text(athlete.name) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.unfavourite(athlete)
                    athleteToUnfavourite = null
                }) {
                    Text("Unfavourite", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { athleteToUnfavourite = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    selectedFavourite?.let { athlete ->
        ModalBottomSheet(
            onDismissRequest = {
                selectedFavourite = null
                detailViewModel.clearViewingAthlete()
            },
            sheetState = favouriteSheetState
        ) {
            AthleteDetailSheet(
                athlete = athlete,
                history = competitionHistory,
                historyLoading = historyLoading,
                historyError = historyError,
                onRefresh = { detailViewModel.refreshAthleteHistory(athlete.slug) }
            )
        }
    }
}

@Composable
private fun SpeedDialItem(
    label: String,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            leadingIcon?.invoke()
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavouriteAthleteCard(
    athleteWithSquad: AthleteWithSquad,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val athlete = athleteWithSquad.athlete
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = athlete.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val sbdParts = listOfNotNull(
                        athlete.bestSquat?.let { Pair("S", formatKgHome(it)) },
                        athlete.bestBench?.let { Pair("B", formatKgHome(it)) },
                        athlete.bestDeadlift?.let { Pair("D", formatKgHome(it)) }
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
            }
            athlete.bestTotal?.let { total ->
                Badge(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Text(
                        text = formatKgHome(total),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SquadCard(
    squad: SquadWithCount,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = squad.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = squad.athleteCount.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun RenameSquadDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    errorMessage: String? = null
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename squad") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Squad name") },
                singleLine = true,
                isError = errorMessage != null,
                supportingText = errorMessage?.let { msg -> { Text(msg) } },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EmptySquadsContent(
    modifier: Modifier = Modifier,
    onCreateSquad: () -> Unit
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_icon),
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(20.dp))
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Track Your Powerlifting Squad",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Follow athletes, monitor PRs, and keep up with competition results — all in one place.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        EmptyStateFeatureRow(icon = Icons.Filled.Star,    text = "Track personal records for squat, bench and deadlift")
        Spacer(Modifier.height(16.dp))
        EmptyStateFeatureRow(icon = Icons.Filled.DateRange, text = "Browse full competition history")
        Spacer(Modifier.height(16.dp))
        EmptyStateFeatureRow(icon = Icons.Filled.Share,   text = "Share squads with friends using a 6-character code")
        Spacer(Modifier.height(40.dp))
        androidx.compose.material3.Button(
            onClick = onCreateSquad,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create Your First Squad")
        }
    }
}

@Composable
private fun EmptyStateFeatureRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CreateSquadDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    errorMessage: String? = null
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New squad") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Squad name") },
                singleLine = true,
                isError = errorMessage != null,
                supportingText = errorMessage?.let { msg -> { Text(msg) } },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ImportSquadDialog(
    loading: Boolean,
    progress: String? = null,
    errorMessage: String? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(if (loading) "Importing squad..." else "Import squad") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!loading) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.uppercase().take(6) },
                        label = { Text("Share code") },
                        placeholder = { Text("e.g. AB3X7K") },
                        singleLine = true,
                        isError = errorMessage != null,
                        supportingText = errorMessage?.let { msg -> { Text(msg) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    progress?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (code.length == 6) onConfirm(code) },
                enabled = code.length == 6 && !loading
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) {
                Text("Cancel")
            }
        }
    )
}
