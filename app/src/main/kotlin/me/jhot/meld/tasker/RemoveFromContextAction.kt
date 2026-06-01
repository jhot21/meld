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

class RemoveFromContextRunner : TaskerPluginRunnerActionNoOutput<ModeNameInput>() {
    override fun run(context: Context, input: TaskerInput<ModeNameInput>): TaskerPluginResult<Unit> {
        val modeName = input.regular.modeName
        val app = context.applicationContext as MeldApplication
        runBlocking { app.modeRepository.removeFromContext(modeName) }
        return TaskerPluginResultSucess()
    }

    override val notificationProperties get() = TaskerPluginRunner.NotificationProperties(
        R.string.tasker_action_remove_from_context,
        R.string.tasker_action_remove_from_context,
        R.string.tasker_action_remove_from_context,
        R.string.tasker_action_remove_from_context,
        R.drawable.ic_launcher_foreground,
    )
}

class RemoveFromContextHelper(config: TaskerPluginConfig<ModeNameInput>) :
    TaskerPluginConfigHelperNoOutput<ModeNameInput, RemoveFromContextRunner>(config) {
    override val runnerClass get() = RemoveFromContextRunner::class.java
    override val inputClass get() = ModeNameInput::class.java
}
