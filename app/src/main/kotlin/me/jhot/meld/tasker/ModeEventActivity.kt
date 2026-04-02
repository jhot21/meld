package me.jhot.meld.tasker

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.joaomgcd.taskerpluginlibrary.SimpleResultError
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.jhot.meld.MeldApplication
import me.jhot.meld.data.model.ModeType
import me.jhot.meld.ui.tasker.TaskerModePickerScreen
import me.jhot.meld.ui.theme.MeldTheme

abstract class ModeEventActivity : ComponentActivity(), TaskerPluginConfig<ModeNameInput> {

    protected abstract fun createHelper(config: TaskerPluginConfig<ModeNameInput>): TaskerPluginConfigHelper<ModeNameInput, *, *>
    protected abstract fun screenTitle(): String

    private val taskerHelper by lazy { createHelper(this) }

    private val modes = mutableStateListOf("Any Mode")
    private var selectedModeName by mutableStateOf("")

    override val context get() = applicationContext
    override val inputForTasker get() = TaskerInput(ModeNameInput(selectedModeName))
    override fun getIntent(): Intent? = super.getIntent()
    override fun finish() = super.finish()

    override fun assignFromInput(input: TaskerInput<ModeNameInput>) {
        selectedModeName = input.regular.modeName
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selectedModeName = savedInstanceState?.getString("selectedModeName") ?: ""

        // Callback stays enabled permanently. On validation failure, taskerHelper.onBackPressed()
        // returns early without calling config.finish(), so the activity stays open.
        // On success, config.finish() is called inside onBackPressed(), closing the activity.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val result = taskerHelper.onBackPressed()
                if (result is SimpleResultError) {
                    Toast.makeText(
                        this@ModeEventActivity,
                        "Settings are not valid: ${result.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })

        lifecycleScope.launch {
            val app = applicationContext as MeldApplication
            val modeNames = withContext(Dispatchers.IO) {
                app.database.modeDao().getAll().first()
                    .filter { it.type != ModeType.DEFAULT }
                    .map { it.name }
            }
            modes.addAll(modeNames)
        }

        setContent {
            MeldTheme {
                TaskerModePickerScreen(
                    title = screenTitle(),
                    modes = modes,
                    selectedMode = if (selectedModeName.isEmpty()) "Any Mode" else selectedModeName,
                    onModeSelected = { mode ->
                        selectedModeName = if (mode == "Any Mode") "" else mode
                    },
                    onSave = { taskerHelper.finishForTasker() }
                )
            }
        }

        taskerHelper.onCreate()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("selectedModeName", selectedModeName)
    }
}

class ModeActivatedActivity : ModeEventActivity() {
    override fun createHelper(config: TaskerPluginConfig<ModeNameInput>) = ModeActivatedHelper(config)
    override fun screenTitle() = "Mode Activated"
}

class ModeDeactivatedActivity : ModeEventActivity() {
    override fun createHelper(config: TaskerPluginConfig<ModeNameInput>) = ModeDeactivatedHelper(config)
    override fun screenTitle() = "Mode Deactivated"
}
