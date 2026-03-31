package me.jhot.meld.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Segmented button row with "—" (null/unset) as the first option, plus typed options.
 *
 * - Selecting "—" calls onSelect(null)
 * - Selecting a typed option calls onSelect(option)
 * - When enabled = false, all buttons are visually disabled and non-interactive
 */
@Composable
fun <T> NullableSegmentedButtonRow(
    options: List<T>,
    selected: T?,
    onSelect: (T?) -> Unit,
    labelFor: (T) -> String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
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
