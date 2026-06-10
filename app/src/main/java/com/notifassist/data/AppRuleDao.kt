package com.notifassist.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppRuleDao {
    @Query("SELECT * FROM app_rules ORDER BY appLabel ASC")
    fun getAllRules(): Flow<List<AppRule>>

    @Query("SELECT * FROM app_rules WHERE packageName = :pkg LIMIT 1")
    suspend fun getRuleForPackage(pkg: String): AppRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: AppRule)

    @Delete
    suspend fun delete(rule: AppRule)

    @Query("DELETE FROM app_rules WHERE packageName = :pkg")
    suspend fun deleteByPackage(pkg: String)
}
