package me.jhot.meld.ui.globalSettings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import me.jhot.meld.service.ImportResult

private const val ADB_COMMAND =
    "adb shell pm grant me.jhot.meld android.permission.WRITE_SECURE_SETTINGS"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSettingsScreen(navController: NavController) {
    val viewModel: GlobalSettingsViewModel = viewModel(factory = GlobalSettingsViewModel.Factory)

    val writeSettingsGranted by viewModel.writeSettingsGranted.collectAsState()
    val notificationPolicyGranted by viewModel.notificationPolicyGranted.collectAsState()
    val secureSettingsGranted by viewModel.secureSettingsGranted.collectAsState()
    val shizukuState by viewModel.shizukuState.collectAsState()

    val context = LocalContext.current

    val importResult by viewModel.importResult.collectAsState()

    var pendingExportJson by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            val json = pendingExportJson ?: return@let
            context.contentResolver.openOutputStream(it)?.use { stream ->
                stream.write(json.toByteArray())
            }
            pendingExportJson = null
        }
    }

    var pendingSingleModeExportJson by remember { mutableStateOf<String?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val singleModeExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            val json = pendingSingleModeExportJson ?: return@let
            context.contentResolver.openOutputStream(it)?.use { stream ->
                stream.write(json.toByteArray())
            }
            pendingSingleModeExportJson = null
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.onImportFilePicked(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.exportReady.collect { (fileName, json) ->
            pendingExportJson = json
            exportLauncher.launch(fileName)
        }
    }

    val allModes by viewModel.allModes.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.singleModeExportReady.collect { (fileName, json) ->
            pendingSingleModeExportJson = json
            singleModeExportLauncher.launch(fileName)
        }
    }

    // Refresh whenever this screen resumes — covers both initial load and return from system settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    TextButton(onClick = { navController.popBackStack() }) {
                        Text("Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Permissions", style = MaterialTheme.typography.titleLarge)
            Text(
                "Meld requires these permissions to apply settings to your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))

            // WRITE_SETTINGS
            PermissionCard(
                title = "Modify system settings",
                description = "Required for volumes, brightness, ringer mode, screen rotation, and display timeout.",
                granted = writeSettingsGranted,
            ) {
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }) {
                    Text("Grant permission")
                }
            }

            // ACCESS_NOTIFICATION_POLICY
            PermissionCard(
                title = "Do Not Disturb access",
                description = "Required for the Do Not Disturb setting.",
                granted = notificationPolicyGranted,
            ) {
                Button(onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }) {
                    Text("Grant permission")
                }
            }

            // WRITE_SECURE_SETTINGS
            PermissionCard(
                title = "Write secure settings",
                description = "Required for dark mode, night light, extra dim, immersive mode, grayscale, haptic feedback, battery saver, location mode, and Bluetooth. Must be granted via ADB or Shizuku.",
                granted = secureSettingsGranted,
            ) {
                Column {
                    if (shizukuState != ShizukuState.Unavailable) {
                        Button(onClick = { viewModel.grantSecureSettingsViaShizuku() }) {
                            Text(
                                if (shizukuState == ShizukuState.NeedsPermission)
                                    "Grant via Shizuku (tap to authorize)"
                                else
                                    "Grant via Shizuku"
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Or use ADB:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "Run this command in a terminal with your phone connected via USB:",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        ADB_COMMAND,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("ADB command", ADB_COMMAND))
                    }) {
                        Text("Copy command")
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("Import / Export", style = MaterialTheme.typography.titleMedium)
            Text(
                "Export all modes to a file or import from a previously exported file.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.onExportClicked() }) {
                    Text("Export")
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("text/plain", "application/json")) }) {
                    Text("Import")
                }
            }

            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it },
            ) {
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    readOnly = true,
                    placeholder = { Text("Export individual mode") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                ) {
                    allModes.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.name) },
                            onClick = {
                                dropdownExpanded = false
                                viewModel.onIndividualExportModeSelected(mode)
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("ntfy.sh Integration", style = MaterialTheme.typography.titleMedium)
            Text(
                "Meld can receive ntfy messages to add or remove modes from your context automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Setup", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "1. Install the ntfy app and subscribe to any topic.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "2. In ntfy, go to Settings and enable \u201cBroadcast messages\u201d.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "3. Send a message to your topic with:",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        Text(
                            "\u2022 Message body: exact mode name (case-sensitive)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "\u2022 Tags must include: meld",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "\u2022 Tags must include one of: add, activate, active, on, enable, enabled, true, 1 \u2014 or their opposites to remove",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "4. Any topic works \u2014 Meld filters by tag, not topic.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // Conflict confirmation dialog
    if (importResult is ImportResult.ConflictsDetected) {
        val conflicts = (importResult as ImportResult.ConflictsDetected).conflictingNames
        AlertDialog(
            onDismissRequest = { viewModel.onImportCancelled() },
            title = { Text("Overwrite existing modes?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("The following modes already exist and will be overwritten:")
                    conflicts.forEach { name ->
                        Text(
                            "• $name",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.onImportConfirmed() }) {
                    Text("Overwrite")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onImportCancelled() }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Error dialogs
    val errorMessage = when (importResult) {
        is ImportResult.MalformedJson -> "The selected file is not a valid Meld export."
        is ImportResult.UnsupportedVersion -> "This export was created with a newer version of Meld. Please update the app and try again."
        else -> null
    }
    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.onImportCancelled() },
            title = { Text("Import failed") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = { viewModel.onImportCancelled() }) {
                    Text("OK")
                }
            },
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    action: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (granted)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (granted) Icons.Default.Check else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!granted) {
                Spacer(Modifier.height(12.dp))
                action()
            }
        }
    }
}
