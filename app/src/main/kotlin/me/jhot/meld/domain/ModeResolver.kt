package me.jhot.meld.domain

import me.jhot.meld.data.model.*

object ModeResolver {

    fun resolve(allModes: List<Mode>, activeModeIds: Set<Long>): ModeSettings {
        val activeModes = allModes.filter { it.id in activeModeIds }
        val activePrimaries = activeModes.filter { it.type == ModeType.PRIMARY }

        // Winning primary: highest-priority active primary, or the default primary if none active.
        // Tie on priority: maxByOrNull returns the last max in the list (highest id among equals).
        val winningPrimary = activePrimaries.maxByOrNull { it.priority }
            ?: allModes.firstOrNull { it.type == ModeType.PRIMARY && it.isDefault }

        val workingSet: List<Mode> = if (winningPrimary != null) {
            val threshold = winningPrimary.priority
            val includedSecondaries = activeModes.filter {
                it.type == ModeType.SECONDARY && it.priority >= threshold
            }
            listOf(winningPrimary) + includedSecondaries
        } else {
            // No primary at all — include all active secondaries
            activeModes.filter { it.type == ModeType.SECONDARY }
        }

        // Sort ascending by priority; at equal priority, secondary sorts after primary (overwrites)
        val sorted = workingSet.sortedWith(
            compareBy<Mode> { it.priority }
                .thenBy { if (it.type == ModeType.SECONDARY) 1 else 0 }
                .thenBy { it.id }
        )

        return sorted.fold(ModeSettings()) { acc, mode -> acc.mergeWith(mode.settings) }
    }
}
