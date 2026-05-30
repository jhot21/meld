package me.jhot.meld.tasker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerAction
import com.joaomgcd.taskerpluginlibrary.runner.ArgsSignalFinish
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Replaces the Tasker library's BroadcastReceiverAction + IntentServiceAction pair.
 *
 * IntentServiceAction starts as a foreground service with a broken zero-icon notification
 * (all resource IDs hardcoded to 0 in the library's default NotificationProperties), logging
 * "Invalid resource ID 0x00000000" on every FIRE_SETTING delivery. The service is final so
 * it cannot be subclassed to override the notification.
 *
 * This receiver handles FIRE_SETTING inline via goAsync(), replicating runWithIntent() without
 * the startForegroundIfNeeded call. All APIs used are public on the library's classes:
 * getInputClass, getTaskerInput, run, getArgsSignalFinish, signalFinish.
 */
class MeldActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val bundle = TaskerInternalBridge.getTaskerPluginExtraBundle(intent) ?: return
        val runnerClassName = TaskerInternalBridge.getRunnerClass(bundle) ?: return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                @Suppress("UNCHECKED_CAST")
                val runner = Class.forName(runnerClassName)
                    .getDeclaredConstructor()
                    .newInstance() as TaskerPluginRunnerAction<Any, Any>

                @Suppress("UNCHECKED_CAST")
                val inputClass = runner.getInputClass(intent) as Class<Any>
                @Suppress("UNCHECKED_CAST")
                val input = TaskerInternalBridge.getTaskerInput(intent, context, inputClass)
                    as com.joaomgcd.taskerpluginlibrary.input.TaskerInput<Any>

                val result = runner.run(context, input)
                result.signalFinish(runner.getArgsSignalFinish(context, intent, input))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to execute Tasker action", e)
                try {
                    TaskerPluginResultError(e)
                        .signalFinish(ArgsSignalFinish(context, intent, null, null))
                } catch (ignored: Exception) {
                    // Best-effort: report error to Tasker if possible
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "MeldActionReceiver"
    }
}
