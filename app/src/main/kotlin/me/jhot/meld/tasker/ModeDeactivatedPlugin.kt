package me.jhot.meld.tasker

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerConditionEvent
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnsatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginRunner
import me.jhot.meld.R

class ModeDeactivatedRunner : TaskerPluginRunnerConditionEvent<ModeNameInput, ModeEventOutput, ModeEventOutput>() {
    override val notificationProperties get() = TaskerPluginRunner.NotificationProperties(
        R.string.tasker_event_mode_deactivated,
        R.string.tasker_event_mode_deactivated,
        R.string.tasker_event_mode_deactivated,
        R.string.tasker_event_mode_deactivated,
        R.drawable.ic_launcher_foreground,
    )

    override fun getSatisfiedCondition(
        context: Context,
        input: TaskerInput<ModeNameInput>,
        update: ModeEventOutput?
    ): TaskerPluginResultCondition<ModeEventOutput> {
        val filterName = input.regular.modeName
        val eventOutput = update ?: return TaskerPluginResultConditionUnsatisfied()
        return if (matchesEventFilter(filterName, eventOutput.modeName)) {
            TaskerPluginResultConditionSatisfied(context)
        } else {
            TaskerPluginResultConditionUnsatisfied()
        }
    }
}

class ModeDeactivatedHelper(config: TaskerPluginConfig<ModeNameInput>) :
    TaskerPluginConfigHelper<ModeNameInput, ModeEventOutput, ModeDeactivatedRunner>(config) {
    override val runnerClass get() = ModeDeactivatedRunner::class.java
    override val inputClass get() = ModeNameInput::class.java
    override val outputClass get() = ModeEventOutput::class.java
}
