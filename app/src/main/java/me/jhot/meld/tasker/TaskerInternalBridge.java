package me.jhot.meld.tasker;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.joaomgcd.taskerpluginlibrary.extensions.InternalKt;
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput;

/**
 * Java bridge to InternalKt methods that are marked `internal` in Kotlin.
 * Java callers ignore Kotlin module-level visibility, so these static methods
 * are accessible here even though Kotlin source in this module cannot call them directly.
 */
final class TaskerInternalBridge {
    private TaskerInternalBridge() {}

    static Bundle getTaskerPluginExtraBundle(Intent intent) {
        return InternalKt.getTaskerPluginExtraBundle(intent);
    }

    static String getRunnerClass(Bundle bundle) {
        return InternalKt.getRunnerClass(bundle);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static TaskerInput getTaskerInput(Intent intent, Context context, Class inputClass) {
        return InternalKt.getTaskerInput(intent, context, inputClass, null);
    }
}
