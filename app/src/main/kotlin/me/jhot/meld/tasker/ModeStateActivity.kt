package me.jhot.meld.tasker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.RadioGroup
import com.joaomgcd.taskerpluginlibrary.SimpleResultError
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
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

class ModeStateActivity : Activity(), TaskerPluginConfig<ModeStateInput> {

    private val taskerHelper by lazy { ModeStateHelper(this) }

    private lateinit var dropdown: AutoCompleteTextView
    private lateinit var directionGroup: RadioGroup
    private var selectedModeName: String = ""
    private var selectedDirection: StateDirection = StateDirection.ACTIVE
    private val activityScope = CoroutineScope(Dispatchers.IO + Job())

    override val context get() = applicationContext
    override val inputForTasker get() = TaskerInput(ModeStateInput(selectedModeName, selectedDirection.name))
    override fun getIntent(): Intent? = super.getIntent()
    override fun finish() = super.finish()

    override fun assignFromInput(input: TaskerInput<ModeStateInput>) {
        selectedModeName = input.regular.modeName
        selectedDirection = StateDirection.valueOf(input.regular.stateDirection)
        if (::dropdown.isInitialized) {
            dropdown.setText(if (selectedModeName.isEmpty()) "Any Mode" else selectedModeName, false)
        }
        if (::directionGroup.isInitialized) {
            directionGroup.check(if (selectedDirection == StateDirection.ACTIVE) R.id.rb_active else R.id.rb_inactive)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasker_state)

        selectedModeName = savedInstanceState?.getString("selectedModeName") ?: ""
        selectedDirection = StateDirection.valueOf(
            savedInstanceState?.getString("selectedDirection") ?: StateDirection.ACTIVE.name
        )

        dropdown = findViewById(R.id.mode_dropdown)
        directionGroup = findViewById(R.id.state_direction_group)

        val app = applicationContext as MeldApplication
        activityScope.launch {
            val modeNames = app.database.modeDao().getAll().first().map { it.name }
            val items = listOf("Any Mode") + modeNames
            withContext(Dispatchers.Main) {
                dropdown.setAdapter(ArrayAdapter(this@ModeStateActivity, android.R.layout.simple_dropdown_item_1line, items))
                dropdown.setText(if (selectedModeName.isEmpty()) "Any Mode" else selectedModeName, false)
                directionGroup.check(if (selectedDirection == StateDirection.ACTIVE) R.id.rb_active else R.id.rb_inactive)
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

        directionGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedDirection = if (checkedId == R.id.rb_active) StateDirection.ACTIVE else StateDirection.INACTIVE
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
        outState.putString("selectedDirection", selectedDirection.name)
    }

    @Deprecated("Use onKeyDown to intercept back") override fun onBackPressed() {}
}
