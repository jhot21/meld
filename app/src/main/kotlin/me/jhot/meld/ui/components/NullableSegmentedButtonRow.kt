package me.jhot.meld.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Segmented button row with "—" (null/unset) as the first option, plus typed options.
 * Automatically switches to a dropdown when there are more than 3 typed options.
 *
 * - Selecting "—" calls onSelect(null)
 * - Selecting a typed option calls onSelect(option)
 * - When enabled = false, all buttons are visually disabled and non-interactive
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> NullableSegmentedButtonRow(
    options: List<T>,
    selected: T?,
    onSelect: (T?) -> Unit,
    labelFor: (T) -> String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (options.size > 3) {
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded && enabled,
            onExpandedChange = { if (enabled) expanded = it },
            modifier = modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = if (selected == null) "Unset" else labelFor(selected),
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Unset") },
                    onClick = { onSelect(null); expanded = false },
                )
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(labelFor(option)) },
                        onClick = { onSelect(option); expanded = false },
                    )
                }
            }
        }
    } else {
        val count = options.size + 1 // "+1" for the "—" option
        SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = selected == null,
                onClick = { if (enabled) onSelect(null) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = count),
            ) {
                Text("—")
            }
            options.forEachIndexed { idx, option ->
                SegmentedButton(
                    selected = selected == option,
                    onClick = { if (enabled) onSelect(option) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index = idx + 1, count = count),
                ) {
                    Text(labelFor(option))
                }
            }
        }
    }
}
