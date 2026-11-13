package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.example.model.AppRuleStatus
import com.example.model.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstalledAppsRepository(
    private val context: Context,
    private val ruleDao: AppSplitRuleDao
) {
    private val cubanPackagePrefixes = listOf(
        "cu.etecsa",
        "cu.xetid",
        "cu.todus",
        "cu.uci",
        "cu.sld",
        "cu.desoft",
        "cu.apklis",
        "cu.picta"
    )

    private val cubanKeywords = listOf(
        "transfermovil",
        "enzona",
        "todus",
        "nauta",
        "etecsa",
        "apklis",
        "picta",
        "bolsami",
        "ticket"
    )

    private val messagingKeywords = listOf(
        "whatsapp",
        "telegram",
        "signal",
        "messenger",
        "instagram",
        "facebook",
        "youtube",
        "chrome",
        "browser"
    )

    suspend fun loadInstalledApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val savedRules = ruleDao.getAllRulesSnapshot().associate {
            it.packageName to AppRuleStatus.fromName(it.ruleStatus)
        }

        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }

        val uniquePackages = linkedMapOf<String, InstalledAppInfo>()
        for (info in resolved) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (pkg == context.packageName || uniquePackages.containsKey(pkg)) continue

            val appInfo = info.activityInfo.applicationInfo
            val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
            val icon = runCatching { info.loadIcon(pm) }.getOrNull()
            val lowerPkg = pkg.lowercase()
            val lowerLabel = label.lowercase()

            val isCuban = cubanPackagePrefixes.any { lowerPkg.startsWith(it) } ||
                cubanKeywords.any { lowerPkg.contains(it) || lowerLabel.contains(it) }

            val isMessaging = messagingKeywords.any {
                lowerPkg.contains(it) || lowerLabel.contains(it)
            }

            val ruleStatus = savedRules[pkg] ?: if (isCuban) {
                AppRuleStatus.BYPASS_DIRECT
            } else {
                AppRuleStatus.VPN_TUNNEL
            }

            uniquePackages[pkg] = InstalledAppInfo(
                packageName = pkg,
                appName = label,
                uid = appInfo.uid,
                icon = icon,
                isCubanApp = isCuban,
                isMessagingOrSocial = isMessaging,
                ruleStatus = ruleStatus
            )
        }

        uniquePackages.values.sortedWith(
            compareByDescending<InstalledAppInfo> { it.isCubanApp }
                .thenBy { it.appName.lowercase() }
        )
    }

    suspend fun setAppRule(packageName: String, status: AppRuleStatus) {
        ruleDao.upsertRule(
            AppSplitRuleEntity(
                packageName = packageName,
                ruleStatus = status.name
            )
        )
    }

    suspend fun applyCubanBypassPreset(apps: List<InstalledAppInfo>) {
        val rules = apps.map { app ->
            val target = if (app.isCubanApp) AppRuleStatus.BYPASS_DIRECT else AppRuleStatus.VPN_TUNNEL
            AppSplitRuleEntity(packageName = app.packageName, ruleStatus = target.name)
        }
        ruleDao.upsertRules(rules)
    }

    suspend fun applyMessagingOnlyWhitelistPreset(apps: List<InstalledAppInfo>) {
        val rules = apps.map { app ->
            val target = if (app.isMessagingOrSocial) AppRuleStatus.VPN_TUNNEL else AppRuleStatus.BYPASS_DIRECT
            AppSplitRuleEntity(packageName = app.packageName, ruleStatus = target.name)
        }
        ruleDao.upsertRules(rules)
    }
}
