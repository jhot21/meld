package me.jhot.meld.tasker

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerCondition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import me.jhot.meld.MeldApplication

/**
 * Computes the symmetric diff between two sets of mode IDs.
 * Returns (added, removed).
 */
internal fun diffSets(previous: Set<Long>, current: Set<Long>): Pair<Set<Long>, Set<Long>> =
    (current - previous) to (previous - current)

/**
 * Observes [ActiveModeDao.getActiveModeIds] as a Flow and notifies Tasker whenever active modes
 * change:
 * - Fires a ModeActivated event for each newly-added mode ID.
 * - Fires a ModeDeactivated event for each newly-removed mode ID.
 * - Sends a requestQuery to Tasker so all ModeState conditions are re-evaluated.
 *
 * Event firing uses [TaskerPluginRunnerCondition.Companion.requestQuery] with a non-null [update]
 * argument. The library serializes the update via [getUpdateBundle] and sends it to Tasker as
 * pass-through data; Tasker delivers it back to the runner's [getSatisfiedCondition] as the
 * [update] parameter, which is how the event plugin library signals an event occurrence.
 *
 * requestQuery is called with the config Activity class that corresponds to each plugin type:
 *   - ModeActivatedActivity  → triggers ModeActivated events
 *   - ModeDeactivatedActivity → triggers ModeDeactivated events
 *   - ModeStateActivity       → asks Tasker to re-check all ModeState conditions
 */
class TaskerBridge(private val context: Context, private val scope: CoroutineScope) {

    fun start() {
        val app = context.applicationContext as MeldApplication
        val activeModeDao = app.activeDatabase.activeModeDao()
        val modeDao = app.database.modeDao()

        scope.launch {
            var previousIds: Set<Long>? = null

            activeModeDao.getActiveModeIds().collect { currentIds ->
                val prev = previousIds
                if (prev == null) {
                    // First emission: store baseline; skip diffing to avoid spurious events.
                    previousIds = currentIds
                    return@collect
                }

                val (added, removed) = diffSets(prev, currentIds)
                previousIds = currentIds

                for (modeId in added) {
                    val mode = modeDao.getById(modeId) ?: continue
                    fireEvent(ModeActivatedActivity::class.java, ModeEventOutput(mode.name, mode.type.name))
                }
                for (modeId in removed) {
                    val mode = modeDao.getById(modeId) ?: continue
                    fireEvent(ModeDeactivatedActivity::class.java, ModeEventOutput(mode.name, mode.type.name))
                }

                if (added.isNotEmpty() || removed.isNotEmpty()) {
                    requestStateQuery()
                }
            }
        }
    }

    /**
     * Fires a Tasker event for the given activity class (which identifies the plugin type).
     * Passing a non-null [output] causes the library to serialize it as pass-through data and
     * deliver it to Tasker, which then re-broadcasts it to [getSatisfiedCondition] as the update.
     */
    private fun fireEvent(activityClass: Class<*>, output: ModeEventOutput) {
        @Suppress("UNCHECKED_CAST")
        TaskerPluginRunnerCondition.Companion.requestQuery(
            context,
            activityClass as Class<android.app.Activity>,
            output
        )
    }

    /**
     * Asks Tasker to re-evaluate all ModeState conditions (no update payload needed for states).
     */
    private fun requestStateQuery() {
        TaskerPluginRunnerCondition.Companion.requestQuery(
            context,
            ModeStateActivity::class.java
        )
    }
}
