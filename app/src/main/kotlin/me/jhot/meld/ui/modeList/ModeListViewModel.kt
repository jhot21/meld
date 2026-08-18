package me.jhot.meld.ui.modeList

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import me.jhot.meld.MeldApplication
import me.jhot.meld.data.model.ExclusivityGroup
import me.jhot.meld.data.model.Mode
import me.jhot.meld.service.ModeRepository
import me.jhot.meld.service.PermissionChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ModeListItem(val mode: Mode, val isActive: Boolean, val groups: List<ExclusivityGroup>)

class ModeListViewModel(
    private val repository: ModeRepository,
    private val permissionChecker: PermissionChecker,
) : ViewModel() {

    val modesWithActiveState: StateFlow<List<ModeListItem>> =
        combine(repository.modesWithActiveState, repository.getAllGroups(), repository.getGroupIdsPerMode()) {
            list, allGroups, groupIdsPerMode ->
            val groupsById = allGroups.associateBy { it.id }
            list.filter { (mode, _) -> !mode.isDefault }
                .map { (mode, isActive) ->
                    val groups = (groupIdsPerMode[mode.id] ?: emptyList()).mapNotNull { groupsById[it] }
                    ModeListItem(mode, isActive, groups)
                }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val defaultMode: StateFlow<Mode?> =
        repository.modesWithActiveState
            .map { list -> list.firstOrNull { (mode, _) -> mode.isDefault }?.first }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _anyPermissionMissing = MutableStateFlow(false)
    val anyPermissionMissing: StateFlow<Boolean> = _anyPermissionMissing.asStateFlow()

    private val _bannerDismissed = MutableStateFlow(false)
    val bannerDismissed: StateFlow<Boolean> = _bannerDismissed.asStateFlow()

    private val _pendingDeleteMode = MutableStateFlow<Mode?>(null)
    val pendingDeleteMode: StateFlow<Mode?> = _pendingDeleteMode.asStateFlow()

    fun checkPermissions() {
        permissionChecker.resetSecureSettingsCache()
        _anyPermissionMissing.value = !permissionChecker.canWriteSettings() ||
            !permissionChecker.canWriteNotificationPolicy() ||
            !permissionChecker.canWriteSecureSettings()
    }

    fun toggleActive(modeId: Long, isActive: Boolean) {
        viewModelScope.launch { repository.setModeActive(modeId, !isActive) }
    }

    fun requestDelete(mode: Mode) { _pendingDeleteMode.value = mode }
    fun cancelDelete() { _pendingDeleteMode.value = null }

    fun confirmDelete() {
        val mode = _pendingDeleteMode.value ?: return
        viewModelScope.launch { repository.deleteMode(mode) }
        _pendingDeleteMode.value = null
    }

    fun dismissBanner() { _bannerDismissed.value = true }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(get(APPLICATION_KEY)) as MeldApplication
                ModeListViewModel(app.modeRepository, app.permissionChecker)
            }
        }
    }
}
