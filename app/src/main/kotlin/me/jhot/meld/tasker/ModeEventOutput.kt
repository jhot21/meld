package me.jhot.meld.tasker

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable

@TaskerInputRoot
@TaskerOutputObject
class ModeEventOutput @JvmOverloads constructor(
    @field:TaskerInputField("meld_mode_name", labelResIdName = "tasker_label_mode_name")
    @get:TaskerOutputVariable("meld_mode_name", labelResIdName = "tasker_label_mode_name") val modeName: String = "",
    @field:TaskerInputField("meld_mode_type", labelResIdName = "tasker_label_mode_type")
    @get:TaskerOutputVariable("meld_mode_type", labelResIdName = "tasker_label_mode_type") val modeType: String = ""
)
