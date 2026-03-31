package me.jhot.meld

import android.app.Application
import android.content.Context
import androidx.room.Room
import me.jhot.meld.data.db.ActiveDatabase
import me.jhot.meld.data.db.MeldDatabase
import me.jhot.meld.service.ModeRepository
import me.jhot.meld.service.OverrideSessionStore
import me.jhot.meld.service.PermissionChecker
import me.jhot.meld.service.SettingsApplier

class MeldApplication : Application() {

    val database: MeldDatabase by lazy {
        Room.databaseBuilder(this, MeldDatabase::class.java, "meld.db").build()
    }

    /** Device-protected storage — accessible before user unlock (for BootReceiver). */
    val activeDatabase: ActiveDatabase by lazy {
        val deviceContext = createDeviceProtectedStorageContext()
        Room.databaseBuilder(deviceContext, ActiveDatabase::class.java, "active.db").build()
    }

    val overrideSessionStore: OverrideSessionStore by lazy {
        OverrideSessionStore(getSharedPreferences("meld_prefs", Context.MODE_PRIVATE))
    }

    val permissionChecker: PermissionChecker by lazy { PermissionChecker(this) }

    val settingsApplier: SettingsApplier by lazy {
        SettingsApplier(this, permissionChecker, overrideSessionStore)
    }

    val modeRepository: ModeRepository by lazy {
        ModeRepository(
            modeDao = database.modeDao(),
            activeModeDao = activeDatabase.activeModeDao(),
            settingsApplier = settingsApplier,
        )
    }
}
