package me.jhot.meld.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.jhot.meld.data.model.ExclusivityGroup

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExclusivityGroupChipRow(
    allGroups: List<ExclusivityGroup>,
    selectedGroupIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newGroupName by remember { mutableStateOf("") }

    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        allGroups.forEach { group ->
            FilterChip(
                selected = group.id in selectedGroupIds,
                onClick = { onToggle(group.id) },
                label = { Text(group.name) },
            )
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
}
