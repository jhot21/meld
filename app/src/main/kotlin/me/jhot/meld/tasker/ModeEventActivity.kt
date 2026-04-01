package me.jhot.meld.tasker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import com.joaomgcd.taskerpluginlibrary.SimpleResultError
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.jhot.meld.MeldApplication
import me.jhot.meld.R

abstract class ModeEventActivity : Activity(), TaskerPluginConfig<ModeNameInput> {

    protected abstract fun createHelper(config: TaskerPluginConfig<ModeNameInput>): TaskerPluginConfigHelper<ModeNameInput, *, *>

    private val taskerHelper by lazy { createHelper(this) }

    private lateinit var dropdown: AutoCompleteTextView
    private var selectedModeName: String = ""
    private val activityScope = CoroutineScope(Dispatchers.IO + Job())

    override val context get() = applicationContext
    override val inputForTasker get() = TaskerInput(ModeNameInput(selectedModeName))
    override fun getIntent(): Intent? = super.getIntent()
    override fun finish() = super.finish()

    override fun assignFromInput(input: TaskerInput<ModeNameInput>) {
        selectedModeName = input.regular.modeName
        if (::dropdown.isInitialized) {
            dropdown.setText(if (selectedModeName.isEmpty()) "Any Mode" else selectedModeName, false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasker_event)

        selectedModeName = savedInstanceState?.getString("selectedModeName") ?: ""

        dropdown = findViewById(R.id.mode_dropdown)

        val app = applicationContext as MeldApplication
        activityScope.launch {
            val modeNames = app.database.modeDao().getAll().first().map { it.name }
            val items = listOf("Any Mode") + modeNames
            withContext(Dispatchers.Main) {
                dropdown.setAdapter(ArrayAdapter(this@ModeEventActivity, android.R.layout.simple_dropdown_item_1line, items))
                dropdown.setText(if (selectedModeName.isEmpty()) "Any Mode" else selectedModeName, false)
            }
        }

        dropdown.setOnItemClickListener { _, _, _, _ ->
            val text = dropdown.text.toString()
            selectedModeName = if (text == "Any Mode") "" else text
        }
        dropdown.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val text = dropdown.text.toString()
                selectedModeName = if (text == "Any Mode") "" else text
            }
        }

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            val text = dropdown.text.toString()
            selectedModeName = if (text == "Any Mode") "" else text
            taskerHelper.finishForTasker()
        }

        taskerHelper.onCreate()
    }

    override fun onDestroy() { super.onDestroy(); activityScope.cancel() }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return if (keyCode == KeyEvent.KEYCODE_BACK && event.repeatCount == 0) {
            val result = taskerHelper.onBackPressed()
            if (result is SimpleResultError) android.widget.Toast.makeText(this, "Settings not valid: ${result.message}", android.widget.Toast.LENGTH_SHORT).show()
            result.success
        } else super.onKeyDown(keyCode, event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("selectedModeName", selectedModeName)
    }

    @Deprecated("Use onKeyDown to intercept back") override fun onBackPressed() {}
}

class ModeActivatedActivity : ModeEventActivity() {
    override fun createHelper(config: TaskerPluginConfig<ModeNameInput>) = ModeActivatedHelper(config)
}

class ModeDeactivatedActivity : ModeEventActivity() {
    override fun createHelper(config: TaskerPluginConfig<ModeNameInput>) = ModeDeactivatedHelper(config)
}
