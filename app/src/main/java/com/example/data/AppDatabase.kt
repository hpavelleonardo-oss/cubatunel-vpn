package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.model.ProxyType

@Database(
    entities = [ProxyProfileEntity::class, AppSplitRuleEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun proxyProfileDao(): ProxyProfileDao
    abstract fun appSplitRuleDao(): AppSplitRuleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cubatunnel_db"
                ).build().also { INSTANCE = it }
            }
        }

        suspend fun ensureDefaultStarterProfiles(dao: ProxyProfileDao) {
            if (dao.count() > 0) return
            val starters = listOf(
                ProxyProfileEntity(
                    name = "Cubacel DNS-DoH + Cortafuegos (Sin Proxy)",
                    proxyType = ProxyType.DOH_DIRECT.name,
                    host = "1.1.1.1",
                    port = 443,
                    dnsServer = "1.1.1.1",
                    customMtu = 1360,
                    keepAliveSeconds = 25,
                    isActive = true,
                    isCubacelOptimized = true,
                    priorityOrder = 0
                ),
                ProxyProfileEntity(
                    name = "Plantilla SOCKS5 Cubacel",
                    proxyType = ProxyType.SOCKS5.name,
                    host = "127.0.0.1",
                    port = 1080,
                    dnsServer = "1.1.1.1",
                    customMtu = 1360,
                    keepAliveSeconds = 25,
                    isActive = false,
                    isCubacelOptimized = true,
                    priorityOrder = 1
                ),
                ProxyProfileEntity(
                    name = "Plantilla HTTP Payload + TLS/SNI",
                    proxyType = ProxyType.WS_TLS_CDN.name,
                    host = "tu-servidor.ejemplo.com",
                    port = 443,
                    sniHost = "tu-sni.ejemplo.com",
                    httpPayload = "GET /ws HTTP/1.1[crlf]Host: [sni][crlf]Upgrade: websocket[crlf]Connection: Upgrade[crlf][crlf]",
                    wsPath = "/ws",
                    dnsServer = "1.1.1.1",
                    customMtu = 1360,
                    keepAliveSeconds = 20,
                    isActive = false,
                    isCubacelOptimized = true,
                    priorityOrder = 2
                )
            )
            starters.forEach { dao.insertProfile(it) }
        }
    }
}
