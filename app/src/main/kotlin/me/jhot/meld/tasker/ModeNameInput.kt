package me.jhot.meld.tasker

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot

@TaskerInputRoot
class ModeNameInput @JvmOverloads constructor(
    @field:TaskerInputField("modeName", labelResIdName = "modeName") var modeName: String = ""
)
