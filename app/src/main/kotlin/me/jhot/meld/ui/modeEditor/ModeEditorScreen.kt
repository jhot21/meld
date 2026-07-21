package me.jhot.meld.ui.modeEditor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import me.jhot.meld.data.model.DndMode
import me.jhot.meld.data.model.ImmersiveMode
import me.jhot.meld.data.model.LocationMode
import me.jhot.meld.data.model.ModeSettings
import me.jhot.meld.data.model.RingerMode
import me.jhot.meld.ui.components.NullableSegmentedButtonRow
import me.jhot.meld.ui.components.SettingsSection
import me.jhot.meld.ui.components.SliderRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeEditorScreen(modeId: Long?, navController: NavController) {
    val viewModel: ModeEditorViewModel = viewModel(factory = ModeEditorViewModel.Factory)

    // Load existing mode once
    LaunchedEffect(modeId) {
        if (modeId != null) viewModel.loadMode(modeId)
    }

    val draft by viewModel.draft.collectAsState()
    val isDefaultMode = draft.isDefault
    val isDirty by viewModel.isDirty.collectAsState()
    val nameConflict by viewModel.nameConflict.collectAsState()
    val saveComplete by viewModel.saveComplete.collectAsState()
    val secureGranted = viewModel.secureSettingsGranted

    // Navigate back when save completes — runs on main thread via LaunchedEffect
    LaunchedEffect(saveComplete) {
        if (saveComplete) navController.popBackStack()
    }

    var showDiscardDialog by remember { mutableStateOf(false) }

    // Back handler — show discard dialog if there are unsaved changes on an existing mode
    BackHandler(enabled = isDirty && !viewModel.isNew) {
        showDiscardDialog = true
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("Your unsaved changes will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    navController.popBackStack()
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Keep editing") }
            },
        )
    }

    val settings = draft.settings

    fun updateSettings(update: ModeSettings.() -> ModeSettings) {
        viewModel.updateSettings(settings.update())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(when {
                        isDefaultMode -> "Default Mode"
                        viewModel.isNew -> "New Mode"
                        else -> "Edit Mode"
                    })
                },
                navigationIcon = {
                    TextButton(onClick = {
                        if (isDirty && !viewModel.isNew) {
                            showDiscardDialog = true
                        } else {
                            navController.popBackStack()
                        }
                    }) { Text("Back") }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.save() },
                        enabled = draft.name.isNotBlank() && !nameConflict,
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                },
                scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {

            // ---- Identity — hidden for the DEFAULT mode -------------------------
            if (!isDefaultMode) {
                SettingsSection(title = "Identity") {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = viewModel::updateName,
                        label = { Text("Name") },
                        singleLine = true,
                        isError = nameConflict,
                        supportingText = if (nameConflict) {
                            { Text("Name already exists") }
                        } else null,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    )

                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Priority: ${draft.priority}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = draft.priority.toFloat(),
                        onValueChange = { viewModel.updatePriority(it.toInt()) },
                        valueRange = 0f..100f,
                        modifier = Modifier.fillMaxWidth(),
                    )

                }
            } // end if (!isDefaultMode)

            HorizontalDivider()

            // ---- Audio ----------------------------------------------------------
            SettingsSection(title = "Audio") {
                SliderRow(
                    label = "Notification Volume",
                    value = settings.volumeNotification,
                    onValueChange = { updateSettings { copy(volumeNotification = it) } },
                    min = 0,
                    max = viewModel.maxNotificationVolume,
                    valueLabel = { it.toString() },
                )
                Spacer(Modifier.height(8.dp))

                SliderRow(
                    label = "Media Volume",
                    value = settings.volumeMedia,
                    onValueChange = { updateSettings { copy(volumeMedia = it) } },
                    min = 0,
                    max = viewModel.maxMediaVolume,
                    valueLabel = { it.toString() },
                )
                Spacer(Modifier.height(8.dp))

                Text("Media Volume Override", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Apply media volume once, then ignore all changes until override is removed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                NullableSegmentedButtonRow(
                    options = listOf(true, false),
                    selected = settings.volumeMediaOverride,
                    onSelect = { updateSettings { copy(volumeMediaOverride = it) } },
                    labelFor = { if (it) "ON" else "OFF" },
                )
                Spacer(Modifier.height(8.dp))

                Text("Ringer Mode", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                NullableSegmentedButtonRow(
                    options = RingerMode.entries,
                    selected = settings.ringerMode,
                    onSelect = { updateSettings { copy(ringerMode = it) } },
                    labelFor = {
                        when (it) {
                            RingerMode.SILENT -> "Silent"
                            RingerMode.VIBRATE -> "Vibrate"
                            RingerMode.SOUND -> "Sound"
                        }
                    },
                )
            }

            HorizontalDivider()

            // ---- Display --------------------------------------------------------
            SettingsSection(title = "Display") {
                SliderRow(
                    label = "Brightness",
                    value = settings.brightness,
                    onValueChange = { updateSettings { copy(brightness = it) } },
                    min = 0,
                    max = 255,
                    valueLabel = { it.toString() },
                )
                Spacer(Modifier.height(4.dp))
                Text("Brightness Auto", style = MaterialTheme.typography.bodyMedium)
                NullableSegmentedButtonRow(
                    options = listOf(true, false),
                    selected = settings.brightnessAuto,
                    onSelect = { updateSettings { copy(brightnessAuto = it) } },
                    labelFor = { if (it) "ON" else "OFF" },
                )
                Spacer(Modifier.height(8.dp))

                // Display Timeout — toggle + number field
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Display Timeout (min)",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = settings.displayTimeout != null,
                        onCheckedChange = { on ->
                            updateSettings { copy(displayTimeout = if (on) 2 else null) }
                        },
                    )
                }
                if (settings.displayTimeout != null) {
                    OutlinedTextField(
                        value = settings.displayTimeout.toString(),
                        onValueChange = { v ->
                            v.toIntOrNull()?.let { updateSettings { copy(displayTimeout = it.coerceIn(1, 120)) } }
                        },
                        label = { Text("Minutes") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))

                Text("Screen Rotation", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                NullableSegmentedButtonRow(
                    options = listOf(true, false),
                    selected = settings.screenRotation,
                    onSelect = { updateSettings { copy(screenRotation = it) } },
                    labelFor = { if (it) "ON" else "OFF" },
                )
                Spacer(Modifier.height(8.dp))

                SecureSettingWrapper(label = "Dark Mode", secureGranted = secureGranted) {
                    NullableSegmentedButtonRow(
                        options = listOf(true, false),
                        selected = settings.darkMode,
                        onSelect = { updateSettings { copy(darkMode = it) } },
                        labelFor = { if (it) "ON" else "OFF" },
                        enabled = secureGranted,
                    )
                }
                Spacer(Modifier.height(8.dp))

                SecureSettingWrapper(label = "Night Light", secureGranted = secureGranted) {
                    NullableSegmentedButtonRow(
                        options = listOf(true, false),
                        selected = settings.nightLight,
                        onSelect = { updateSettings { copy(nightLight = it) } },
                        labelFor = { if (it) "ON" else "OFF" },
                        enabled = secureGranted,
                    )
                }
                Spacer(Modifier.height(8.dp))

                SecureSettingWrapper(label = "Extra Dim", secureGranted = secureGranted) {
                    NullableSegmentedButtonRow(
                        options = listOf(true, false),
                        selected = settings.extraDim,
                        onSelect = { updateSettings { copy(extraDim = it) } },
                        labelFor = { if (it) "ON" else "OFF" },
                        enabled = secureGranted,
                    )
                }
                Spacer(Modifier.height(8.dp))

                SecureSettingWrapper(label = "Immersive Mode", secureGranted = secureGranted) {
                    NullableSegmentedButtonRow(
                        options = ImmersiveMode.entries,
                        selected = settings.immersiveMode,
                        onSelect = { updateSettings { copy(immersiveMode = it) } },
                        labelFor = {
                            when (it) {
                                ImmersiveMode.OFF -> "Off"
                                ImmersiveMode.STATUS_BAR -> "Status bar"
                                ImmersiveMode.NAV_BAR -> "Nav bar"
                                ImmersiveMode.BOTH -> "Both"
                            }
                        },
                        enabled = secureGranted,
                    )
                }
                Spacer(Modifier.height(8.dp))

                SecureSettingWrapper(label = "Grayscale", secureGranted = secureGranted) {
                    NullableSegmentedButtonRow(
                        options = listOf(true, false),
                        selected = settings.grayscale,
                        onSelect = { updateSettings { copy(grayscale = it) } },
                        labelFor = { if (it) "ON" else "OFF" },
                        enabled = secureGranted,
                    )
                }
            }

            HorizontalDivider()

            // ---- System ---------------------------------------------------------
            SettingsSection(title = "System") {
                Text("Do Not Disturb", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                NullableSegmentedButtonRow(
                    options = DndMode.entries,
                    selected = settings.dnd,
                    onSelect = { updateSettings { copy(dnd = it) } },
                    labelFor = {
                        when (it) {
                            DndMode.OFF -> "Off"
                            DndMode.PRIORITY_ONLY -> "Priority"
                            DndMode.ALARMS_ONLY -> "Alarms"
                            DndMode.TOTAL_SILENCE -> "Silent"
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))

                SecureSettingWrapper(label = "Haptic Feedback", secureGranted = secureGranted) {
                    NullableSegmentedButtonRow(
                        options = listOf(true, false),
                        selected = settings.hapticFeedback,
                        onSelect = { updateSettings { copy(hapticFeedback = it) } },
                        labelFor = { if (it) "ON" else "OFF" },
                        enabled = secureGranted,
                    )
                }
                Spacer(Modifier.height(8.dp))

                SecureSettingWrapper(label = "Keyboard Vibration", secureGranted = secureGranted) {
                    NullableSegmentedButtonRow(
                        options = listOf(true, false),
                        selected = settings.keyboardVibration,
                        onSelect = { updateSettings { copy(keyboardVibration = it) } },
                        labelFor = { if (it) "ON" else "OFF" },
                        enabled = secureGranted,
                    )
                }
                Spacer(Modifier.height(8.dp))

                SecureSettingWrapper(label = "Battery Saver", secureGranted = secureGranted) {
                    NullableSegmentedButtonRow(
                        options = listOf(true, false),
                        selected = settings.batterySaver,
                        onSelect = { updateSettings { copy(batterySaver = it) } },
                        labelFor = { if (it) "ON" else "OFF" },
                        enabled = secureGranted,
                    )
                }
                Spacer(Modifier.height(8.dp))

                SecureSettingWrapper(label = "Location Mode", secureGranted = secureGranted) {
                    NullableSegmentedButtonRow(
                        options = LocationMode.entries,
                        selected = settings.locationMode,
                        onSelect = { updateSettings { copy(locationMode = it) } },
                        labelFor = {
                            when (it) {
                                LocationMode.OFF -> "Off"
                                LocationMode.HIGH_ACCURACY -> "High accuracy"
                                LocationMode.BATTERY_SAVER -> "Battery saver"
                                LocationMode.DEVICE_ONLY -> "Device only"
                            }
                        },
                        enabled = secureGranted,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * Wrapper that shows a lock icon + "ADB required" label when [secureGranted] is false.
 * Always renders [content] — the NullableSegmentedButtonRow inside should pass enabled = secureGranted.
 */
@Composable
private fun SecureSettingWrapper(
    label: String,
    secureGranted: Boolean,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (!secureGranted) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "ADB required",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(modifier = if (!secureGranted) Modifier.alpha(0.5f) else Modifier) {
            content()
        }
    }
}
