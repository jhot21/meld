package me.jhot.meld.ui.modeEditor

import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import me.jhot.meld.MeldApplication
import me.jhot.meld.data.model.ExclusivityGroup
import me.jhot.meld.data.model.Mode
import me.jhot.meld.data.model.ModeSettings
import me.jhot.meld.service.ModeRepository
import me.jhot.meld.service.PermissionChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ModeEditorViewModel(
    private val repository: ModeRepository,
    private val permissionChecker: PermissionChecker,
    val maxMediaVolume: Int,
    val maxNotificationVolume: Int,
) : ViewModel() {

    private val _draft = MutableStateFlow(
        Mode(name = "", isDefault = false, priority = 50),
    )
    val draft: StateFlow<Mode> = _draft.asStateFlow()

    private var originalMode: Mode? = null

    val isDirty: StateFlow<Boolean> = _draft
        .map { it != originalMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isNew: Boolean get() = originalMode == null

    /** True when editing the always-active DEFAULT mode. */
    val isDefaultMode: Boolean get() = _draft.value.isDefault

    /** True if WRITE_SECURE_SETTINGS is granted (cached — won't change mid-session). */
    val secureSettingsGranted: Boolean get() = permissionChecker.canWriteSecureSettings()

    /**
     * True when the current draft name matches an existing mode that is NOT the mode being edited.
     * Case-insensitive. False when the name is blank.
     */
    val nameConflict: StateFlow<Boolean> = combine(_draft, repository.getAllModes()) { draft, modes ->
        if (draft.name.isBlank()) return@combine false
        val conflict = modes.find { it.name.equals(draft.name, ignoreCase = true) }
        conflict != null && conflict.id != draft.id
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _saveComplete = MutableStateFlow(false)
    val saveComplete: StateFlow<Boolean> = _saveComplete.asStateFlow()

    val allGroups: StateFlow<List<ExclusivityGroup>> =
        repository.getAllGroups().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _draftGroupIds = MutableStateFlow<Set<Long>>(emptySet())
    val draftGroupIds: StateFlow<Set<Long>> = _draftGroupIds.asStateFlow()

    fun toggleGroup(groupId: Long) {
        _draftGroupIds.update { current ->
            if (groupId in current) current - groupId else current + groupId
        }
    }

    fun createAndJoinGroup(name: String) {
        viewModelScope.launch {
            val groupId = repository.getOrCreateGroupByName(name)
            _draftGroupIds.update { it + groupId }
        }
    }

    fun loadMode(modeId: Long) {
        _saveComplete.value = false
        val cached = repository.getModeByIdNow(modeId)
        if (cached != null) {
            originalMode = cached
            _draft.value = cached
            viewModelScope.launch { _draftGroupIds.value = repository.getGroupsForMode(modeId).map { it.id }.toSet() }
            return
        }
        viewModelScope.launch {
            val mode = repository.getAllModes().first().find { it.id == modeId } ?: return@launch
            originalMode = mode
            _draft.value = mode
            _draftGroupIds.value = repository.getGroupsForMode(modeId).map { it.id }.toSet()
        }
    }

    fun updateName(name: String) {
        _draft.update { it.copy(name = name, updatedAt = System.currentTimeMillis()) }
    }

    fun updatePriority(priority: Int) {
        _draft.update { it.copy(priority = priority.coerceIn(0, 100), updatedAt = System.currentTimeMillis()) }
    }

    fun updateSettings(settings: ModeSettings) {
        _draft.update { it.copy(settings = settings, updatedAt = System.currentTimeMillis()) }
    }

    fun save() {
        if (nameConflict.value) return
        viewModelScope.launch {
            val d = _draft.value
            val modeId = if (isNew) repository.insertMode(d) else { repository.updateMode(d); d.id }
            repository.updateModeGroups(modeId, _draftGroupIds.value.toList())
            _saveComplete.value = true
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(get(APPLICATION_KEY)) as MeldApplication
                val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                ModeEditorViewModel(app.modeRepository, app.permissionChecker, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION))
            }
        }
    }
}
