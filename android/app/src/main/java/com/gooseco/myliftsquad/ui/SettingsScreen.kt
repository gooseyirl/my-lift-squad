package com.gooseco.myliftsquad.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gooseco.myliftsquad.ui.viewmodel.BackupStatus
import com.gooseco.myliftsquad.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val exportStatus by viewModel.exportStatus.collectAsState()
    val restoreStatus by viewModel.restoreStatus.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportBackup(it) } }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            pendingRestoreUri = it
            showRestoreConfirm = true
        }
    }

    LaunchedEffect(exportStatus) {
        when (exportStatus) {
            BackupStatus.Success -> {
                snackbarHostState.showSnackbar("Backup exported successfully.")
                viewModel.clearExportStatus()
            }
            BackupStatus.Error -> {
                snackbarHostState.showSnackbar("Export failed. Please try again.")
                viewModel.clearExportStatus()
            }
            else -> {}
        }
    }

    LaunchedEffect(restoreStatus) {
        when (restoreStatus) {
            BackupStatus.Success -> {
                snackbarHostState.showSnackbar("Backup restored successfully.")
                viewModel.clearRestoreStatus()
            }
            BackupStatus.Error -> {
                snackbarHostState.showSnackbar("Restore failed. Please check the file and try again.")
                viewModel.clearRestoreStatus()
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Spacer(Modifier.height(8.dp))

            SettingsSectionHeader("Backup & Restore")

            SettingsItem(
                title = "Export Backup",
                subtitle = "Save your squads and athletes to a file"
            ) {
                exportLauncher.launch("myliftsquad-backup.json")
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            SettingsItem(
                title = "Restore Backup",
                subtitle = "Load squads and athletes from a backup file. This will overwrite all current data."
            ) {
                restoreLauncher.launch(arrayOf("application/json", "*/*"))
            }
        }
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = {
                showRestoreConfirm = false
                pendingRestoreUri = null
            },
            title = { Text("Restore backup?") },
            text = { Text("This will permanently overwrite all your current squads and athletes. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestoreUri?.let { viewModel.restoreBackup(it) }
                    showRestoreConfirm = false
                    pendingRestoreUri = null
                }) {
                    Text("Restore", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    pendingRestoreUri = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
