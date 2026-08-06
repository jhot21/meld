package me.jhot.meld.ui.modeList

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import me.jhot.meld.data.model.ExclusivityGroup
import me.jhot.meld.data.model.Mode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeListScreen(navController: NavController) {
    val viewModel: ModeListViewModel = viewModel(factory = ModeListViewModel.Factory)

    val modes by viewModel.modesWithActiveState.collectAsState()
    val defaultMode by viewModel.defaultMode.collectAsState()
    val anyPermissionMissing by viewModel.anyPermissionMissing.collectAsState()
    val bannerDismissed by viewModel.bannerDismissed.collectAsState()
    val pendingDeleteMode by viewModel.pendingDeleteMode.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.checkPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Delete confirmation dialog
    pendingDeleteMode?.let { mode ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Delete ${mode.name}?") },
            text = { Text("This mode will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) { Text("Cancel") }
            },
        )
    }

    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meld") },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = {
                                menuExpanded = false
                                navController.navigate("globalSettings")
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("modeEditor") }) {
                Icon(Icons.Default.Add, contentDescription = "Create mode")
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            // Permission banner
            if (anyPermissionMissing && !bannerDismissed) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Some permissions are missing — settings may not apply.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { navController.navigate("globalSettings") }) {
                            Text("Fix")
                        }
                        IconButton(onClick = viewModel::dismissBanner) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (modes.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No modes yet — tap + to create your first mode.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(modes, key = { item -> item.mode.id }) { item ->
                        ModeListRow(
                            mode = item.mode,
                            isActive = item.isActive,
                            groups = item.groups,
                            onToggle = { viewModel.toggleActive(item.mode.id, item.isActive) },
                            onRequestDelete = { viewModel.requestDelete(item.mode) },
                            onClick = { navController.navigate("modeEditor?modeId=${item.mode.id}") },
                            onRenameGroup = viewModel::renameGroup,
                            onDeleteGroup = viewModel::deleteGroup,
                        )
                    }
                }
            }
            defaultMode?.let { mode ->
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                Text(
                    "Default",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                DefaultModeItem(
                    mode = mode,
                    onClick = { navController.navigate("modeEditor?modeId=${mode.id}") },
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun DefaultModeItem(
    mode: Mode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(mode.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Always active",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ModeListRow(
    mode: Mode,
    isActive: Boolean,
    groups: List<ExclusivityGroup>,
    onToggle: () -> Unit,
    onRequestDelete: () -> Unit,
    onClick: () -> Unit,
    onRenameGroup: (Long, String) -> Unit,
    onDeleteGroup: (Long) -> Unit,
) {
    var menuForGroupId by remember { mutableStateOf<Long?>(null) }
    var renameTargetId by remember { mutableStateOf<Long?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTargetId by remember { mutableStateOf<Long?>(null) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRequestDelete()
            }
            false // always snap back — confirmation dialog handles actual deletion
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.padding(end = 24.dp),
                )
            }
        },
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isActive)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surface,
            ),
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(mode.name, style = MaterialTheme.typography.titleMedium)
                    if (groups.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            groups.forEach { group ->
                                Box(
                                    modifier = Modifier.combinedClickable(
                                        onClick = {},
                                        onLongClick = { menuForGroupId = group.id },
                                    ),
                                ) {
                                    SuggestionChip(onClick = {}, label = { Text(group.name) })
                                    DropdownMenu(
                                        expanded = menuForGroupId == group.id,
                                        onDismissRequest = { menuForGroupId = null },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Rename") },
                                            onClick = {
                                                renameTargetId = group.id
                                                renameText = group.name
                                                menuForGroupId = null
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete") },
                                            onClick = {
                                                deleteTargetId = group.id
                                                menuForGroupId = null
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        "Priority ${mode.priority}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Switch(
                        checked = isActive,
                        onCheckedChange = { onToggle() },
                    )
                    Text(
                        if (isActive) "Active" else "Inactive",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    renameTargetId?.let { groupId ->
        AlertDialog(
            onDismissRequest = { renameTargetId = null },
            title = { Text("Rename group") },
            text = {
                OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) onRenameGroup(groupId, renameText)
                    renameTargetId = null
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetId = null }) { Text("Cancel") }
            },
        )
    }

    deleteTargetId?.let { groupId ->
        val name = groups.find { it.id == groupId }?.name ?: ""
        AlertDialog(
            onDismissRequest = { deleteTargetId = null },
            title = { Text("Delete \"$name\"?") },
            text = { Text("This removes the group from every mode that uses it.") },
            confirmButton = {
                TextButton(onClick = { onDeleteGroup(groupId); deleteTargetId = null }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetId = null }) { Text("Cancel") }
            },
        )
    }
}
