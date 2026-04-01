package me.jhot.meld.tasker

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot

enum class StateDirection { ACTIVE, INACTIVE }

@TaskerInputRoot
class ModeStateInput @JvmOverloads constructor(
    @field:TaskerInputField("modeName", labelResIdName = "modeName") var modeName: String = "",
    @field:TaskerInputField("stateDirection", labelResIdName = "stateDirection") var stateDirection: String = StateDirection.ACTIVE.name
)
