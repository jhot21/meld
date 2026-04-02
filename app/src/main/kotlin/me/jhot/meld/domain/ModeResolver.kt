package me.jhot.meld.domain

import me.jhot.meld.data.model.*

object ModeResolver {

    fun resolve(allModes: List<Mode>, activeModeIds: Set<Long>): ModeSettings {
        val defaultSettings = allModes.find { it.type == ModeType.DEFAULT }?.settings ?: ModeSettings()

        val activeModes = allModes.filter { it.type != ModeType.DEFAULT && it.id in activeModeIds }
        val activePrimaries = activeModes.filter { it.type == ModeType.PRIMARY }

        // Winning primary: highest-priority active primary (ties broken by highest id).
        val winningPrimary = activePrimaries.maxByOrNull { it.priority }

        val workingSet: List<Mode> = if (winningPrimary != null) {
            val includedSecondaries = activeModes.filter {
                it.type == ModeType.SECONDARY
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

        return sorted.fold(defaultSettings) { acc, mode -> acc.mergeWith(mode.settings) }
    }
}
