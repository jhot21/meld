package me.jhot.meld.service

import android.content.SharedPreferences

class OverrideSessionStore(private val prefs: SharedPreferences) {

    fun isActive(): Boolean = prefs.getBoolean(KEY, false)

    fun setActive() {
        prefs.edit().putBoolean(KEY, true).apply()
    }

    fun clear() {
        prefs.edit().putBoolean(KEY, false).apply()
    }

    companion object {
        private const val KEY = "volume_override_active"
    }
}
