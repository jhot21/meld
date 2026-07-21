package me.jhot.meld.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "exclusivity_groups", indices = [Index(value = ["name"], unique = true)])
data class ExclusivityGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(tableName = "mode_exclusivity_group_cross_ref", primaryKeys = ["modeId", "groupId"])
data class ModeExclusivityGroupCrossRef(
    val modeId: Long,
    val groupId: Long,
)
