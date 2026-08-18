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
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.jhot.meld.MeldApplication
import me.jhot.meld.ui.tasker.ModeStateScreen
import me.jhot.meld.ui.theme.MeldTheme

class ModeStateActivity : ComponentActivity(), TaskerPluginConfig<ModeStateInput> {

    private val taskerHelper by lazy { ModeStateHelper(this) }

    private val modes = mutableStateListOf("Any Mode")
    private var selectedModeName by mutableStateOf("")
    private var selectedDirection by mutableStateOf(StateDirection.ACTIVE)

    override val context get() = applicationContext
    override val inputForTasker get() = TaskerInput(ModeStateInput(selectedModeName, selectedDirection.name))
    override fun getIntent(): Intent? = super.getIntent()
    override fun finish() = super.finish()

    override fun assignFromInput(input: TaskerInput<ModeStateInput>) {
        selectedModeName = input.regular.modeName
        selectedDirection = StateDirection.valueOf(input.regular.stateDirection)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selectedModeName = savedInstanceState?.getString("selectedModeName") ?: ""
        selectedDirection = StateDirection.valueOf(
            savedInstanceState?.getString("selectedDirection") ?: StateDirection.ACTIVE.name
        )

        // Callback stays enabled permanently. On validation failure, taskerHelper.onBackPressed()
        // returns early without calling config.finish(), so the activity stays open.
        // On success, config.finish() is called inside onBackPressed(), closing the activity.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val result = taskerHelper.onBackPressed()
                if (result is SimpleResultError) {
                    Toast.makeText(
                        this@ModeStateActivity,
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
                    .filter { !it.isDefault }
                    .map { it.name }
            }
            modes.addAll(modeNames)
        }

        setContent {
            MeldTheme {
                ModeStateScreen(
                    modes = modes,
                    selectedMode = if (selectedModeName.isEmpty()) "Any Mode" else selectedModeName,
                    onModeSelected = { mode ->
                        selectedModeName = if (mode == "Any Mode") "" else mode
                    },
                    direction = selectedDirection,
                    onDirectionChanged = { selectedDirection = it },
                    onSave = { taskerHelper.finishForTasker() }
                )
            }
        }

        taskerHelper.onCreate()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("selectedModeName", selectedModeName)
        outState.putString("selectedDirection", selectedDirection.name)
    }
}
