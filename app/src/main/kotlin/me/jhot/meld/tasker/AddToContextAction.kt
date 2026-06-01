package me.jhot.meld.tasker

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerActionNoOutput
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelperNoOutput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginRunner
import kotlinx.coroutines.runBlocking
import me.jhot.meld.MeldApplication
import me.jhot.meld.R

class AddToContextRunner : TaskerPluginRunnerActionNoOutput<ModeNameInput>() {
    override fun run(context: Context, input: TaskerInput<ModeNameInput>): TaskerPluginResult<Unit> {
        val modeName = input.regular.modeName
        val app = context.applicationContext as MeldApplication
        runBlocking { app.modeRepository.addToContext(modeName) }
        return TaskerPluginResultSucess()
    }

    override val notificationProperties get() = TaskerPluginRunner.NotificationProperties(
        R.string.tasker_action_add_to_context,
        R.string.tasker_action_add_to_context,
        R.string.tasker_action_add_to_context,
        R.string.tasker_action_add_to_context,
        R.drawable.ic_launcher_foreground,
    )
}

class AddToContextHelper(config: TaskerPluginConfig<ModeNameInput>) :
    TaskerPluginConfigHelperNoOutput<ModeNameInput, AddToContextRunner>(config) {
    override val runnerClass get() = AddToContextRunner::class.java
    override val inputClass get() = ModeNameInput::class.java
}
