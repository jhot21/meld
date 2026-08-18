package me.jhot.meld.service

import me.jhot.meld.data.model.Mode

internal fun buildNotificationBody(modesWithActiveState: List<Pair<Mode, Boolean>>): String {
    val activeNonDefault = modesWithActiveState
        .filter { (mode, isActive) -> isActive && !mode.isDefault }
        .sortedByDescending { (mode, _) -> mode.priority }
        .map { (mode, _) -> mode.name }
    return if (activeNonDefault.isEmpty()) {
        "Only default mode active"
    } else {
        val label = if (activeNonDefault.size == 1) "mode" else "modes"
        "${activeNonDefault.size} $label active: ${activeNonDefault.joinToString(", ")}"
    }
}
