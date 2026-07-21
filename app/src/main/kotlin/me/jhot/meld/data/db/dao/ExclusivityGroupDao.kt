package me.jhot.meld.data.db.dao

import androidx.room.*
import me.jhot.meld.data.model.ExclusivityGroup
import me.jhot.meld.data.model.ModeExclusivityGroupCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface ExclusivityGroupDao {
    @Query("SELECT * FROM exclusivity_groups ORDER BY name ASC")
    fun getAllGroups(): Flow<List<ExclusivityGroup>>

    @Query("""
        SELECT g.* FROM exclusivity_groups g
        INNER JOIN mode_exclusivity_group_cross_ref x ON x.groupId = g.id
        WHERE x.modeId = :modeId
    """)
    suspend fun getGroupsForModeOnce(modeId: Long): List<ExclusivityGroup>

    @Query("SELECT modeId, groupId FROM mode_exclusivity_group_cross_ref")
    fun getAllCrossRefs(): Flow<List<ModeExclusivityGroupCrossRef>>

    @Query("SELECT * FROM exclusivity_groups WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getGroupByName(name: String): ExclusivityGroup?

    @Insert
    suspend fun insertGroup(group: ExclusivityGroup): Long

    @Query("UPDATE exclusivity_groups SET name = :name WHERE id = :groupId")
    suspend fun renameGroup(groupId: Long, name: String)

    @Query("DELETE FROM exclusivity_groups WHERE id = :groupId")
    suspend fun deleteGroupById(groupId: Long)

    @Query("DELETE FROM mode_exclusivity_group_cross_ref WHERE groupId = :groupId")
    suspend fun deleteCrossRefsForGroup(groupId: Long)

    @Transaction
    suspend fun deleteGroup(groupId: Long) {
        deleteCrossRefsForGroup(groupId)
        deleteGroupById(groupId)
    }

    @Query("DELETE FROM mode_exclusivity_group_cross_ref WHERE modeId = :modeId")
    suspend fun deleteCrossRefsForMode(modeId: Long)

    @Insert
    suspend fun insertCrossRef(crossRef: ModeExclusivityGroupCrossRef)

    @Query("DELETE FROM exclusivity_groups WHERE id NOT IN (SELECT DISTINCT groupId FROM mode_exclusivity_group_cross_ref)")
    suspend fun deleteGroupsWithNoMembers()

    @Transaction
    suspend fun setModeGroups(modeId: Long, groupIds: List<Long>) {
        deleteCrossRefsForMode(modeId)
        for (groupId in groupIds.distinct()) {
            insertCrossRef(ModeExclusivityGroupCrossRef(modeId, groupId))
        }
        deleteGroupsWithNoMembers()
    }

    @Transaction
    suspend fun getOrCreateGroupByName(name: String): Long {
        getGroupByName(name)?.let { return it.id }
        return insertGroup(ExclusivityGroup(name = name))
    }
}
