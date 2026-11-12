package me.jhot.meld.service

/** Pure query logic behind [ModeStateProvider], kept apart from Android classes so it is JVM-testable. */
class ModeStateQuery(
    private val repo: ModeRepository,
    private val isSharingEnabled: () -> Boolean,
) {
    /**
     * Names for the URI path segment [path]: `modes` (all non-default modes) or `active` (modes
     * in effect). Returns null for an unknown path; throws [SecurityException] when sharing is off.
     */
    suspend fun names(path: String): List<String>? {
        if (!isSharingEnabled()) throw SecurityException("Mode sharing is disabled")
        return when (path) {
            PATH_MODES -> repo.allModeNames()
            PATH_ACTIVE -> repo.effectiveModeNames()
            else -> null
        }
    }

    companion object {
        const val PATH_MODES = "modes"
        const val PATH_ACTIVE = "active"
    }
}
