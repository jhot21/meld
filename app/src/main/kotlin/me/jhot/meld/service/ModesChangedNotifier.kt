package me.jhot.meld.service

/** Told by [ModeRepository] whenever the set of modes in effect really changes. */
fun interface ModesChangedNotifier {
    /** [activeModeNames] is sorted alphabetically and excludes the Default mode. */
    fun onChanged(activeModeNames: List<String>)

    companion object {
        val None = ModesChangedNotifier { }
    }
}
