package me.jhot.meld.ui.tasker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import me.jhot.meld.tasker.StateDirection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeStateScreen(
    modes: List<String>,
    selectedMode: String,
    onModeSelected: (String) -> Unit,
    direction: StateDirection,
    onDirectionChanged: (StateDirection) -> Unit,
    onSave: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Mode State") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            ModeDropdown(
                modes = modes,
                selectedMode = selectedMode,
                onModeSelected = onModeSelected,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("State Direction")
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = direction == StateDirection.ACTIVE,
                        onClick = { onDirectionChanged(StateDirection.ACTIVE) },
                        role = Role.RadioButton
                    )
            ) {
                RadioButton(selected = direction == StateDirection.ACTIVE, onClick = null)
                Text("Active", modifier = Modifier.padding(start = 8.dp))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = direction == StateDirection.INACTIVE,
                        onClick = { onDirectionChanged(StateDirection.INACTIVE) },
                        role = Role.RadioButton
                    )
            ) {
                RadioButton(selected = direction == StateDirection.INACTIVE, onClick = null)
                Text("Inactive", modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }
}
