package me.jhot.meld.tasker

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerConditionState
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnsatisfied
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.jhot.meld.MeldApplication

/** Pure function — also called directly in unit tests. */
internal fun evaluateCondition(activeIds: Set<Long>, modeId: Long?, direction: StateDirection): Boolean {
    val raw = if (modeId == null) activeIds.isNotEmpty() else modeId in activeIds
    return if (direction == StateDirection.ACTIVE) raw else !raw
}

class ModeStateRunner : TaskerPluginRunnerConditionState<ModeStateInput, Unit>() {
    override fun getSatisfiedCondition(
        context: Context,
        input: TaskerInput<ModeStateInput>,
        update: Unit?
    ): TaskerPluginResultCondition<Unit> {
        val app = context.applicationContext as MeldApplication
        val modeName = input.regular.modeName
        val direction = StateDirection.valueOf(input.regular.stateDirection)

        val activeIds = runBlocking { app.activeDatabase.activeModeDao().getActiveModeIds().first() }

        val modeId: Long? = if (modeName.isEmpty()) {
            null  // Any Mode
        } else {
            runBlocking { app.database.modeDao().getByName(modeName)?.id }
        }

        return if (evaluateCondition(activeIds, modeId, direction)) {
            TaskerPluginResultConditionSatisfied(context)
        } else {
            TaskerPluginResultConditionUnsatisfied()
        }
    }
}

class ModeStateHelper(config: TaskerPluginConfig<ModeStateInput>) :
    TaskerPluginConfigHelper<ModeStateInput, Unit, ModeStateRunner>(config) {
    override val runnerClass get() = ModeStateRunner::class.java
    override val inputClass get() = ModeStateInput::class.java
    override val outputClass get() = Unit::class.java
}
