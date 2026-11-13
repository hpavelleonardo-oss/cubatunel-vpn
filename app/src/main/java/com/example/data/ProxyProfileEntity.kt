package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.example.model.ProxyType
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "proxy_profiles")
data class ProxyProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val proxyType: String,
    val host: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
    val sniHost: String = "",
    val httpPayload: String = "CONNECT [host_port] HTTP/1.1[crlf]Host: [sni][crlf]Connection: Keep-Alive[crlf][crlf]",
    val wsPath: String = "/ws",
    val encryptionMethod: String = "chacha20-poly1305",
    val uuidOrKey: String = "",
    val dnsServer: String = "1.1.1.1",
    val customMtu: Int = 1360,
    val keepAliveSeconds: Int = 25,
    val isActive: Boolean = false,
    val isCubacelOptimized: Boolean = true,
    val lastLatencyMs: Long? = null,
    val priorityOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun resolvedProxyType(): ProxyType = ProxyType.fromName(proxyType)
}

@Dao
interface ProxyProfileDao {
    @Query("SELECT * FROM proxy_profiles ORDER BY isActive DESC, priorityOrder ASC, createdAt DESC")
    fun observeAllProfiles(): Flow<List<ProxyProfileEntity>>

    @Query("SELECT * FROM proxy_profiles ORDER BY isActive DESC, priorityOrder ASC, createdAt DESC")
    suspend fun getAllProfilesSnapshot(): List<ProxyProfileEntity>

    @Query("SELECT * FROM proxy_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProfile(): ProxyProfileEntity?

    @Query("SELECT * FROM proxy_profiles WHERE isActive = 1 LIMIT 1")
    fun observeActiveProfile(): Flow<ProxyProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProxyProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: ProxyProfileEntity)

    @Query("UPDATE proxy_profiles SET isActive = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setActiveProfile(id: Long)

    @Query("UPDATE proxy_profiles SET lastLatencyMs = :latencyMs WHERE id = :id")
    suspend fun updateLatency(id: Long, latencyMs: Long?)

    @Query("DELETE FROM proxy_profiles WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM proxy_profiles")
    suspend fun count(): Int
}
