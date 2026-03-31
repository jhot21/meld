package me.jhot.meld.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

/**
 * A toggle switch + slider row for range settings (volume, brightness).
 *
 * - When [value] is null: switch is off, slider is dimmed and non-interactive.
 * - When [value] is non-null: switch is on, slider is active.
 * - Toggling off sets value to null. Toggling on restores the last non-null value (or midpoint).
 * - [valueLabel]: optional function to render the current value as a string beside the label.
 */
@Composable
fun SliderRow(
    label: String,
    value: Int?,
    onValueChange: (Int?) -> Unit,
    min: Int,
    max: Int,
    modifier: Modifier = Modifier,
    valueLabel: ((Int) -> String)? = null,
) {
    val lastNonNull = remember { mutableIntStateOf(value ?: ((min + max) / 2)) }
    SideEffect {
        if (value != null) lastNonNull.intValue = value
    }

    val isSet = value != null

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (isSet && valueLabel != null) "$label: ${valueLabel(value!!)}" else label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = isSet,
                onCheckedChange = { checked ->
                    onValueChange(if (checked) lastNonNull.intValue else null)
                },
            )
        }
        Slider(
            value = (value ?: lastNonNull.intValue).toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            enabled = isSet,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .alpha(if (isSet) 1f else 0.38f),
        )
    }
}
