package me.jhot.meld.domain

import me.jhot.meld.data.model.*

object ModeResolver {

    fun resolve(
        allModes: List<Mode>,
        activeModeIds: Set<Long>,
        modeGroupIds: Map<Long, List<Long>>,
    ): ModeSettings {
        val defaultSettings = allModes.find { it.isDefault }?.settings ?: ModeSettings()
        val activeModes = allModes.filter { !it.isDefault && it.id in activeModeIds }

        fun groupsOf(mode: Mode) = modeGroupIds[mode.id] ?: emptyList()

        val groupWinners = activeModes
            .flatMap { mode -> groupsOf(mode).map { groupId -> groupId to mode } }
            .groupBy({ it.first }, { it.second })
            .values
            .mapNotNull { members -> members.maxByOrNull { it.priority } }
            .toSet()

        val ungrouped = activeModes.filter { groupsOf(it).isEmpty() }

        val workingSet = (groupWinners + ungrouped).distinctBy { it.id }

        val sorted = workingSet.sortedWith(
            compareBy<Mode> { it.priority }
                .thenBy { if (groupsOf(it).isEmpty()) 1 else 0 }
                .thenBy { it.id }
        )

        return sorted.fold(defaultSettings) { acc, mode -> acc.mergeWith(mode.settings) }
    }
}
