package me.jhot.meld.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "active_modes")
data class ActiveMode(
    @PrimaryKey
    val modeId: Long,
    val activatedAt: Long = System.currentTimeMillis(),
)
