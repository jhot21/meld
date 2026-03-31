package me.jhot.meld.ui.globalSettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import me.jhot.meld.MeldApplication
import me.jhot.meld.service.PermissionChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GlobalSettingsViewModel(
    private val permissionChecker: PermissionChecker,
) : ViewModel() {

    private val _writeSettingsGranted = MutableStateFlow(false)
    val writeSettingsGranted: StateFlow<Boolean> = _writeSettingsGranted.asStateFlow()

    private val _notificationPolicyGranted = MutableStateFlow(false)
    val notificationPolicyGranted: StateFlow<Boolean> = _notificationPolicyGranted.asStateFlow()

    private val _secureSettingsGranted = MutableStateFlow(false)
    val secureSettingsGranted: StateFlow<Boolean> = _secureSettingsGranted.asStateFlow()

    /** Call this on initial load and on every ON_RESUME to pick up permission changes. */
    fun refresh() {
        _writeSettingsGranted.value = permissionChecker.canWriteSettings()
        _notificationPolicyGranted.value = permissionChecker.canWriteNotificationPolicy()
        // Force re-check WRITE_SECURE_SETTINGS (cache must be cleared to pick up ADB grant)
        permissionChecker.resetSecureSettingsCache()
        _secureSettingsGranted.value = permissionChecker.canWriteSecureSettings()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(get(APPLICATION_KEY)) as MeldApplication
                GlobalSettingsViewModel(app.permissionChecker)
            }
        }
    }
}
