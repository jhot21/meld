package me.jhot.meld.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "modes", indices = [Index(value = ["name"], unique = true)])
@TypeConverters(ModeSettingsConverter::class)
data class Mode(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: ModeType,
    val priority: Int,
    val settings: ModeSettings = ModeSettings(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
