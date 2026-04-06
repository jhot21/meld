package me.jhot.meld.ui.globalSettings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import me.jhot.meld.MeldApplication
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

    val allModes: StateFlow<List<Mode>> = modeRepository.getAllModes()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _singleModeExportReady = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 1)
    val singleModeExportReady: SharedFlow<Pair<String, String>> = _singleModeExportReady.asSharedFlow()

    private val shizukuPermissionListener =
        rikka.shizuku.Shizuku.OnRequestPermissionResultListener { _, result ->
            if (result == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                grantSecureSettingsViaShizuku()
            }
        }

    init {
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
        val exportedModes: List<ModeExport> = when (result) {
            is ImportResult.ConflictsDetected -> result.modes
            is ImportResult.Ready -> result.modes
            else -> return
        }
        viewModelScope.launch {
            val modeEntities = exportedModes.map { export ->
                Mode(
                    name = export.name,
                    type = export.type,
                    priority = export.priority,
                    settings = export.settings,
                )
            }
            modeRepository.importModes(modeEntities)
            _importResult.value = null
        }
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
