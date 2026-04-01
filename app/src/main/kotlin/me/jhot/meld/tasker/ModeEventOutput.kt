package me.jhot.meld.tasker

import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable

@TaskerOutputObject
class ModeEventOutput @JvmOverloads constructor(
    @get:TaskerOutputVariable("meld_mode_name", labelResIdName = "meld_mode_name") val modeName: String = "",
    @get:TaskerOutputVariable("meld_mode_type", labelResIdName = "meld_mode_type") val modeType: String = ""
)
