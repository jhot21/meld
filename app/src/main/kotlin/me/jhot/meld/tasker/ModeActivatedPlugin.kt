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

/** Pure function — also called directly in unit tests. */
internal fun matchesEventFilter(filterModeName: String, eventModeName: String): Boolean =
    filterModeName.isEmpty() || filterModeName == eventModeName

class ModeActivatedRunner : TaskerPluginRunnerConditionEvent<ModeNameInput, ModeEventOutput, ModeEventOutput>() {
    override val notificationProperties get() = TaskerPluginRunner.NotificationProperties(
        R.string.tasker_event_mode_activated,
        R.string.tasker_event_mode_activated,
        R.string.tasker_event_mode_activated,
        R.string.tasker_event_mode_activated,
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

class ModeActivatedHelper(config: TaskerPluginConfig<ModeNameInput>) :
    TaskerPluginConfigHelper<ModeNameInput, ModeEventOutput, ModeActivatedRunner>(config) {
    override val runnerClass get() = ModeActivatedRunner::class.java
    override val inputClass get() = ModeNameInput::class.java
    override val outputClass get() = ModeEventOutput::class.java
}
