package me.jhot.meld.tasker

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerConditionState
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnsatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginRunner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.jhot.meld.MeldApplication
import me.jhot.meld.R

/** Pure function — also called directly in unit tests. */
internal fun evaluateCondition(activeIds: Set<Long>, modeId: Long?, direction: StateDirection): Boolean {
    val raw = if (modeId == null) activeIds.isNotEmpty() else modeId in activeIds
    return if (direction == StateDirection.ACTIVE) raw else !raw
}

class ModeStateRunner : TaskerPluginRunnerConditionState<ModeStateInput, Unit>() {
    override val notificationProperties get() = TaskerPluginRunner.NotificationProperties(
        R.string.tasker_condition_mode_state,
        R.string.tasker_condition_mode_state,
        R.string.tasker_condition_mode_state,
        R.string.tasker_condition_mode_state,
        R.drawable.ic_launcher_foreground,
    )

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

// QUERY_CONDITION implementation note (Option B):
// No custom BroadcastReceiver or IntentService subclass is needed for QUERY_CONDITION.
// The taskerpluginlibrary 0.4.10 AAR manifest already registers two built-in entry points:
//   - com.joaomgcd.taskerpluginlibrary.condition.BroadcastReceiverCondition (final)
//   - com.joaomgcd.taskerpluginlibrary.condition.IntentServiceCondition (not final, but unnecessary)
// Both are registered for com.twofortyfouram.locale.intent.action.QUERY_CONDITION and are
// merged into the app's merged manifest automatically via Gradle manifest merger.
// When Tasker fires QUERY_CONDITION it embeds the runner class name in the plugin bundle extras;
// the library's built-in receivers use Class.forName() to instantiate ModeStateRunner and
// invoke getSatisfiedCondition(). No additional manifest entry or subclass is required.
// TaskerBridge (when implemented) should call
//   TaskerPluginRunnerCondition.Companion.requestQuery(context, ModeStateActivity::class.java)
// to ask Tasker to re-evaluate all ModeState conditions whenever active modes change.
class ModeStateHelper(config: TaskerPluginConfig<ModeStateInput>) :
    TaskerPluginConfigHelper<ModeStateInput, Unit, ModeStateRunner>(config) {
    override val runnerClass get() = ModeStateRunner::class.java
    override val inputClass get() = ModeStateInput::class.java
    override val outputClass get() = Unit::class.java
}
