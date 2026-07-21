package me.jhot.meld.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.jhot.meld.data.model.ExclusivityGroup

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExclusivityGroupChipRow(
    allGroups: List<ExclusivityGroup>,
    selectedGroupIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newGroupName by remember { mutableStateOf("") }
    var menuForGroupId by remember { mutableStateOf<Long?>(null) }
    var renameTargetId by remember { mutableStateOf<Long?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTargetId by remember { mutableStateOf<Long?>(null) }

    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        allGroups.forEach { group ->
            Box(
                modifier = Modifier.combinedClickable(
                    onClick = { onToggle(group.id) },
                    onLongClick = { menuForGroupId = group.id },
                ),
            ) {
                FilterChip(
                    selected = group.id in selectedGroupIds,
                    onClick = {},
                    label = { Text(group.name) },
                )
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

    Row {
        OutlinedTextField(
            value = newGroupName,
            onValueChange = { newGroupName = it },
            label = { Text("New group") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = {
            if (newGroupName.isNotBlank()) {
                onCreate(newGroupName)
                newGroupName = ""
            }
        }) {
            Icon(Icons.Default.Add, contentDescription = "Create group")
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
                    if (renameText.isNotBlank()) onRename(groupId, renameText)
                    renameTargetId = null
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetId = null }) { Text("Cancel") }
            },
        )
    }

    deleteTargetId?.let { groupId ->
        val name = allGroups.find { it.id == groupId }?.name ?: ""
        AlertDialog(
            onDismissRequest = { deleteTargetId = null },
            title = { Text("Delete \"$name\"?") },
            text = { Text("This removes the group from every mode that uses it.") },
            confirmButton = {
                TextButton(onClick = { onDelete(groupId); deleteTargetId = null }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetId = null }) { Text("Cancel") }
            },
        )
    }
}
