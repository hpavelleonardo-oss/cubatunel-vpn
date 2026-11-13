package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "app_split_rules")
data class AppSplitRuleEntity(
    @PrimaryKey val packageName: String,
    val ruleStatus: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface AppSplitRuleDao {
    @Query("SELECT * FROM app_split_rules")
    fun observeAllRules(): Flow<List<AppSplitRuleEntity>>

    @Query("SELECT * FROM app_split_rules")
    suspend fun getAllRulesSnapshot(): List<AppSplitRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(rule: AppSplitRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRules(rules: List<AppSplitRuleEntity>)

    @Query("DELETE FROM app_split_rules WHERE packageName = :packageName")
    suspend fun deleteRule(packageName: String)

    @Query("DELETE FROM app_split_rules")
    suspend fun clearAll()
}
