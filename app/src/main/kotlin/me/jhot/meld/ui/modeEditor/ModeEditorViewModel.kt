package me.jhot.meld.ui.modeEditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import me.jhot.meld.MeldApplication
import me.jhot.meld.data.model.Mode
import me.jhot.meld.data.model.ModeSettings
import me.jhot.meld.data.model.ModeType
import me.jhot.meld.service.ModeRepository
import me.jhot.meld.service.PermissionChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ModeEditorViewModel(
    private val repository: ModeRepository,
    private val permissionChecker: PermissionChecker,
) : ViewModel() {

    private val _draft = MutableStateFlow(
        Mode(name = "", type = ModeType.PRIMARY, priority = 50),
    )
    val draft: StateFlow<Mode> = _draft.asStateFlow()

    private var originalMode: Mode? = null

    val isDirty: StateFlow<Boolean> = _draft
        .map { it != originalMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isNew: Boolean get() = originalMode == null

    /** True if WRITE_SECURE_SETTINGS is granted (cached — won't change mid-session). */
    val secureSettingsGranted: Boolean get() = permissionChecker.canWriteSecureSettings()

    private val _saveComplete = MutableStateFlow(false)
    val saveComplete: StateFlow<Boolean> = _saveComplete.asStateFlow()

    fun loadMode(modeId: Long) {
        _saveComplete.value = false
        viewModelScope.launch {
            val mode = repository.getAllModes().first().find { it.id == modeId } ?: return@launch
            originalMode = mode
            _draft.value = mode
        }
    }

    fun updateName(name: String) {
        _draft.update { it.copy(name = name, updatedAt = System.currentTimeMillis()) }
    }

    fun updateType(type: ModeType) {
        _draft.update { it.copy(type = type, updatedAt = System.currentTimeMillis()) }
    }

    fun updatePriority(priority: Int) {
        _draft.update { it.copy(priority = priority.coerceIn(0, 100), updatedAt = System.currentTimeMillis()) }
    }

    fun updateIsDefault(isDefault: Boolean) {
        _draft.update { it.copy(isDefault = isDefault, updatedAt = System.currentTimeMillis()) }
    }

    fun updateSettings(settings: ModeSettings) {
        _draft.update { it.copy(settings = settings, updatedAt = System.currentTimeMillis()) }
    }

    fun save() {
        viewModelScope.launch {
            val d = _draft.value
            if (isNew) {
                val id = repository.insertMode(d)
                if (d.isDefault) repository.setDefault(id)
            } else {
                repository.updateMode(d)
                if (d.isDefault) repository.setDefault(d.id)
            }
            _saveComplete.value = true
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(get(APPLICATION_KEY)) as MeldApplication
                ModeEditorViewModel(app.modeRepository, app.permissionChecker)
            }
        }
    }
}
