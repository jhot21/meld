package me.jhot.meld.tasker

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerConditionEvent
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnsatisfied

class ModeDeactivatedRunner : TaskerPluginRunnerConditionEvent<ModeNameInput, ModeEventOutput, ModeEventOutput>() {
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
