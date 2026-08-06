package me.jhot.meld.ui.globalSettings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import me.jhot.meld.MeldApplication
import me.jhot.meld.data.model.ExclusivityGroup
import me.jhot.meld.data.model.Mode
import me.jhot.meld.service.ImportExportService
import me.jhot.meld.service.ImportResult
import me.jhot.meld.service.ModeExport
import me.jhot.meld.service.ModeRepository
import me.jhot.meld.service.PermissionChecker
import me.jhot.meld.service.ShizukuGranter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ShizukuState {
    data object Unavailable : ShizukuState
    data object NeedsPermission : ShizukuState
    data object Ready : ShizukuState
}

class GlobalSettingsViewModel(
    private val permissionChecker: PermissionChecker,
    private val packageName: String,
    private val importExportService: ImportExportService,
    private val modeRepository: ModeRepository,
) : ViewModel() {

    private val _writeSettingsGranted = MutableStateFlow(false)
    val writeSettingsGranted: StateFlow<Boolean> = _writeSettingsGranted.asStateFlow()

    private val _notificationPolicyGranted = MutableStateFlow(false)
    val notificationPolicyGranted: StateFlow<Boolean> = _notificationPolicyGranted.asStateFlow()

    private val _secureSettingsGranted = MutableStateFlow(false)
    val secureSettingsGranted: StateFlow<Boolean> = _secureSettingsGranted.asStateFlow()

    private val _shizukuState = MutableStateFlow<ShizukuState>(ShizukuState.Unavailable)
    val shizukuState: StateFlow<ShizukuState> = _shizukuState.asStateFlow()

    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult: StateFlow<ImportResult?> = _importResult.asStateFlow()

    private val _exportReady = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 1)
    val exportReady: SharedFlow<Pair<String, String>> = _exportReady.asSharedFlow()

    private val _singleModeExportReady = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 1)
    val singleModeExportReady: SharedFlow<Pair<String, String>> = _singleModeExportReady.asSharedFlow()

    val allModes: StateFlow<List<Mode>> = modeRepository.getAllModes()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allGroupsForImport: StateFlow<List<String>> =
        modeRepository.getAllGroups()
            .map { groups -> groups.map { it.name } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allGroups: StateFlow<List<ExclusivityGroup>> =
        modeRepository.getAllGroups().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun renameGroup(groupId: Long, name: String) {
        viewModelScope.launch { modeRepository.renameGroup(groupId, name) }
    }

    fun deleteGroup(groupId: Long) {
        viewModelScope.launch { modeRepository.deleteGroup(groupId) }
    }

    private val shizukuPermissionListener =
        rikka.shizuku.Shizuku.OnRequestPermissionResultListener { _, result ->
            if (result == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                grantSecureSettingsViaShizuku()
            }
        }

    init {
        refresh()
        rikka.shizuku.Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
    }

    override fun onCleared() {
        super.onCleared()
        rikka.shizuku.Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
    }

    /** Call this on initial load and on every ON_RESUME to pick up permission changes. */
    fun refresh() {
        _writeSettingsGranted.value = permissionChecker.canWriteSettings()
        _notificationPolicyGranted.value = permissionChecker.canWriteNotificationPolicy()
        permissionChecker.resetSecureSettingsCache()
        _secureSettingsGranted.value = permissionChecker.canWriteSecureSettings()
        _shizukuState.value = when {
            !ShizukuGranter.isAvailable() -> ShizukuState.Unavailable
            !ShizukuGranter.hasPermission() -> ShizukuState.NeedsPermission
            else -> ShizukuState.Ready
        }
    }

    fun grantSecureSettingsViaShizuku() {
        if (!ShizukuGranter.hasPermission()) {
            ShizukuGranter.requestPermission(SHIZUKU_REQUEST_CODE)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            ShizukuGranter.grantSecureSettings(packageName)
            withContext(Dispatchers.Main) { refresh() }
        }
    }

    fun onExportClicked() {
        viewModelScope.launch {
            val json = importExportService.export()
            val fileName = importExportService.exportFileName()
            _exportReady.emit(fileName to json)
        }
    }

    fun onIndividualExportModeSelected(mode: Mode) {
        viewModelScope.launch {
            val json = importExportService.exportSingleMode(mode)
            val fileName = importExportService.singleModeExportFileName(mode.name)
            _singleModeExportReady.emit(fileName to json)
        }
    }

    fun onImportFilePicked(uri: Uri) {
        viewModelScope.launch {
            _importResult.value = importExportService.parseImport(uri)
        }
    }

    fun onImportConfirmed() {
        val result = _importResult.value
        val (modes, legacyPrimaryNames) = when (result) {
            is ImportResult.ConflictsDetected -> result.modes to result.legacyPrimaryNames
            is ImportResult.Ready -> result.modes to result.legacyPrimaryNames
            else -> return
        }
        viewModelScope.launch {
            if (legacyPrimaryNames.isNotEmpty()) {
                val existingModes = modeRepository.getAllModes().first()
                val preselected = legacyPrimaryNames.associateWith { name ->
                    existingModes.find { it.name == name }
                        ?.let { modeRepository.getGroupsForMode(it.id).map { g -> g.name } }
                        ?: emptyList()
                }
                _importResult.value = ImportResult.NeedsExclusivityGroupAssignment(modes, legacyPrimaryNames, preselected)
            } else {
                finishImport(modes, modes.associate { it.name to it.groupNames })
            }
        }
    }

    fun onExclusivityGroupsAssigned(assignments: Map<String, List<String>>) {
        val result = _importResult.value as? ImportResult.NeedsExclusivityGroupAssignment ?: return
        val groupNames = result.modes.associate { it.name to (assignments[it.name] ?: it.groupNames) }
        viewModelScope.launch { finishImport(result.modes, groupNames) }
    }

    private suspend fun finishImport(modes: List<ModeExport>, groupNames: Map<String, List<String>>) {
        val modeEntities = modes.map { export ->
            Mode(name = export.name, isDefault = export.isDefault, priority = export.priority, settings = export.settings)
        }
        modeRepository.importModes(modeEntities, groupNames)
        _importResult.value = null
    }

    fun onImportCancelled() {
        _importResult.value = null
    }

    companion object {
        private const val SHIZUKU_REQUEST_CODE = 1001

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(get(APPLICATION_KEY)) as MeldApplication
                GlobalSettingsViewModel(
                    permissionChecker = app.permissionChecker,
                    packageName = app.packageName,
                    importExportService = app.importExportService,
                    modeRepository = app.modeRepository,
                )
            }
        }
    }
}
