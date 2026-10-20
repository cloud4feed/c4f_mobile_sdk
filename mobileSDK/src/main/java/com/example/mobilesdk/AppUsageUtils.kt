package com.c4f.mobileSDK

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi

object AppUsageUtils {
    
    fun getAppUsageData(context: Context): Map<String, String> {
        val data = mutableMapOf<String, String>()
        
        // Check if we have usage stats permission (API 23+)
        val hasUsageStatsPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkUsageStatsPermission(context)
        } else {
            // For API < 23, permissions are granted at installation time
            true
        }
        
        data["has_usage_stats_permission"] = hasUsageStatsPermission.toString()
        
        if (hasUsageStatsPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Add your usage stats collection code here with proper API checks
            data["usage_stats_available"] = "true"
        } else {
            data["usage_stats_available"] = "false"
        }
        
        return data
    }
    
    @RequiresApi(Build.VERSION_CODES.M)
    private fun checkUsageStatsPermission(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == 
               PackageManager.PERMISSION_GRANTED
    }
    
    // Update other methods to include API level checks
    fun getEnhancedSessionData(context: Context): Map<String, String> {
        val data = mutableMapOf<String, String>()
        
        data["session_enhanced"] = "true"

        // Metrics tracked by ExclusionRuleEvaluator.trackSessionStart / trackScreenView
        val prefs = context.getSharedPreferences("survey_sdk_session", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        data["session_count"] = prefs.getInt("session_count", 0).toString()
        data["screen_view_count"] = prefs.getInt("screen_view_count", 0).toString()

        val sessionStart = prefs.getLong("session_start_time", 0L)
        if (sessionStart > 0L) {
            data["session_duration_minutes"] = ((now - sessionStart) / 60_000L).coerceAtLeast(0L).toString()
            data["session_duration_seconds"] = ((now - sessionStart) / 1_000L).coerceAtLeast(0L).toString()
        }

        // Minutes between the previous session start and now (absent on the first session)
        val previousSession = prefs.getLong("previous_session_time", 0L)
        if (previousSession > 0L) {
            data["time_since_last_session_minutes"] = ((now - previousSession) / 60_000L).coerceAtLeast(0L).toString()
        }
        
        return data
    }
    
    fun getAppInstallInfo(context: Context): Map<String, String> {
        val data = mutableMapOf<String, String>()
        
        // Safe to use on all API levels
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        data["app_version"] = packageInfo.versionName ?: "unknown"

        // Install time (epoch ms) and elapsed time since install
        val installTime = packageInfo.firstInstallTime
        val elapsed = (System.currentTimeMillis() - installTime).coerceAtLeast(0L)
        data["app_install_time"] = installTime.toString()
        data["days_since_install"] = (elapsed / 86_400_000L).toString()
        data["hours_since_install"] = (elapsed / 3_600_000L).toString()
        data["minutes_since_install"] = (elapsed / 60_000L).toString()
        data["app_version_code"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toString()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toString()
        }
        
        return data
    }
}