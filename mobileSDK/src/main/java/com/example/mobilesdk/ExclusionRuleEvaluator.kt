package com.c4f.mobileSDK

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object ExclusionRuleEvaluator {

    // Session storage for in-memory data during app lifetime
    private val sessionStorage = mutableMapOf<String, String>()

    // Supplies the name of the screen the SDK last tracked (set by MobileSDK.autoSetup)
    @Volatile
    internal var currentScreenProvider: (() -> String?)? = null
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    // Add session data (for SESSION source)
    fun setSessionData(key: String, value: String) {
        sessionStorage[key] = value
        Log.d("MobileSDK", "Session data set: $key = $value")
    }

    fun clearSessionData() {
        sessionStorage.clear()
        Log.d("MobileSDK", "Session data cleared")
    }

    fun shouldExcludeSurvey(context: Context, rules: List<ExclusionRule>): Boolean {
        if (rules.isEmpty()) return false

        Log.d("MobileSDK", "=== CHECKING ${rules.size} EXCLUSION RULES ===")

        for (rule in rules) {
            if (evaluateRule(context, rule)) {
                Log.d("MobileSDK", "🚫 User excluded by rule: ${rule.name}")
                return true
            }
        }
        return false
    }

    private fun evaluateRule(context: Context, rule: ExclusionRule): Boolean {
        return try {
            val actualValue = getValueFromSource(context, rule)
            
            val matchValue = if (rule.caseSensitive) rule.matchValue else rule.matchValue.lowercase(Locale.ROOT)
            val compareValue = if (rule.caseSensitive) actualValue else actualValue.lowercase(Locale.ROOT)

            Log.d("MobileSDK", "🔍 Rule '${rule.name}': '$actualValue' ${rule.operator} '$matchValue'")

            val result = when (rule.operator) {
                ExclusionOperator.EQUALS -> compareValue == matchValue
                ExclusionOperator.NOT_EQUALS -> compareValue != matchValue
                ExclusionOperator.CONTAINS -> compareValue.contains(matchValue)
                ExclusionOperator.NOT_CONTAINS -> !compareValue.contains(matchValue)
                ExclusionOperator.STARTS_WITH -> compareValue.startsWith(matchValue)
                ExclusionOperator.ENDS_WITH -> compareValue.endsWith(matchValue)
                
                // 🔥 NEW: Logic for LENGTH (13)
                ExclusionOperator.LENGTH -> compareNumericValues(actualValue.length.toString(), matchValue) { a, b -> a == b }
                
                ExclusionOperator.GREATER_THAN -> compareNumericValues(actualValue, matchValue) { a, b -> a > b }
                ExclusionOperator.LESS_THAN -> compareNumericValues(actualValue, matchValue) { a, b -> a < b }
                ExclusionOperator.GREATER_OR_EQUAL -> compareNumericValues(actualValue, matchValue) { a, b -> a >= b }
                ExclusionOperator.LESS_OR_EQUAL -> compareNumericValues(actualValue, matchValue) { a, b -> a <= b }
                ExclusionOperator.IN -> isValueInList(compareValue, matchValue, !rule.caseSensitive)
                ExclusionOperator.NOT_IN -> !isValueInList(compareValue, matchValue, !rule.caseSensitive)
                ExclusionOperator.EMPTY -> actualValue.isBlank()
                ExclusionOperator.NOT_EMPTY -> actualValue.isNotBlank()
            }

            Log.d("MobileSDK", "📊 Rule '${rule.name}' result: $result")
            result

        } catch (e: Exception) {
            Log.e("MobileSDK", "Error evaluating exclusion rule '${rule.name}': ${e.message}")
            false
        }
    }

    // Check if value is in comma-separated list (case handling follows the rule's caseSensitive flag)
    private fun isValueInList(value: String, listString: String, ignoreCase: Boolean): Boolean {
        return try {
            val items = listString.split(",").map { it.trim() }
            items.any { it.equals(value, ignoreCase = ignoreCase) }
        } catch (e: Exception) {
            false
        }
    }

    private fun getValueFromSource(context: Context, rule: ExclusionRule): String {
        return when (rule.source) {
            ExclusionSource.STORAGE -> {
                StorageUtils.findSpecificData(context, rule.key ?: rule.name) ?: ""
            }
            ExclusionSource.DEVICE -> {
                getDeviceValue(context, rule.key ?: rule.name) ?: ""
            }
            ExclusionSource.URL -> {
                getUrlParameter(context, rule.key ?: rule.name) ?: ""
            }
            ExclusionSource.TIMESTAMP -> {
                getTimestampValue(rule.key ?: "millis")
            }
            ExclusionSource.SESSION -> {
                val sessionKey = rule.key ?: rule.name
                // Values set through setSessionData() win; otherwise fall back to automatic session metrics
                sessionStorage[sessionKey] ?: getAutomaticSessionValue(context, sessionKey) ?: ""
            }
            ExclusionSource.INSTALL_TIME -> {
                getInstallTimeValue(context, rule.key ?: "days")
            }
            ExclusionSource.APP_USAGE -> {
                getAppUsageValue(context, rule.key ?: "sessions") ?: ""
            }
        }
    }

    // ===== DEVICE VALUES =====
    internal fun getDeviceValue(context: Context, key: String): String? {
        val deviceInfo = DeviceUtils.getDeviceInfo(context)
        return deviceInfo[key] ?: run {
            // Additional device-specific values
            when (key.toLowerCase()) {
                "battery_level" -> getBatteryLevel(context)
                "is_charging" -> isDeviceCharging(context).toString()
                "is_wifi" -> isWifiConnected(context).toString()
                "is_roaming" -> isNetworkRoaming(context).toString()
                "charge_type" -> getBatteryChargeType(context)
                "latitude" -> getLocationValue(context, "latitude")
                "longitude" -> getLocationValue(context, "longitude")
                "location_accuracy" -> getLocationValue(context, "location_accuracy")
                "country" -> Locale.getDefault().country.takeIf { it.isNotBlank() }
                "screensize" -> deviceInfo["screenResolution"]
                else -> null
            }
        }
    }

    private fun getBatteryLevel(context: Context): String {
        return try {
            val batteryInfo = DeviceUtils.getBatteryInfo(context)
            batteryInfo["battery_level"] ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun getBatteryChargeType(context: Context): String {
        return try {
            val batteryInfo = DeviceUtils.getBatteryInfo(context)
            batteryInfo["charge_type"] ?: "none"
        } catch (e: Exception) {
            "none"
        }
    }

    private fun isDeviceCharging(context: Context): Boolean {
        return try {
            val batteryInfo = DeviceUtils.getBatteryInfo(context)
            batteryInfo["is_charging"] == "true"
        } catch (e: Exception) {
            false
        }
    }

    private fun isWifiConnected(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val networkInfo = connectivityManager.getNetworkInfo(android.net.ConnectivityManager.TYPE_WIFI)
            networkInfo?.isConnected == true
        } catch (e: Exception) {
            false
        }
    }

    private fun isNetworkRoaming(context: Context): Boolean {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
            telephonyManager.isNetworkRoaming
        } catch (e: Exception) {
            false
        }
    }

    private fun getLocationValue(context: Context, key: String): String {
        return try {
            val locationData = DeviceUtils.getLocationData(context)
            locationData[key] ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    // ===== URL PARAMETERS =====
    internal fun getUrlParameter(context: Context, key: String): String? {
        return try {
            // Check if context is an Activity and get intent data
            if (context is android.app.Activity) {
                // Check deep link URL parameters
                context.intent?.data?.getQueryParameter(key)?.let { return it }

                // Check intent extras
                context.intent?.extras?.getString(key)?.let { return it }

                // Check intent data string
                context.intent?.getStringExtra(key)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    // ===== TIMESTAMP VALUES =====
    internal fun getTimestampValue(format: String): String {
        return when (format) {
            "millis" -> System.currentTimeMillis().toString()
            "seconds" -> (System.currentTimeMillis() / 1000).toString()
            "iso" -> dateFormatter.format(Date(System.currentTimeMillis()))
            "hour" -> Calendar.getInstance().get(Calendar.HOUR_OF_DAY).toString()
            "day_of_week" -> Calendar.getInstance().get(Calendar.DAY_OF_WEEK).toString()
            "day_of_month" -> Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toString()
            "month" -> (Calendar.getInstance().get(Calendar.MONTH) + 1).toString()
            else -> System.currentTimeMillis().toString()
        }
    }

    // ===== AUTOMATIC SESSION VALUES =====
    // Metrics the SDK tracks itself (see trackSessionStart / trackScreenView).
    // Returns null for unknown keys so the rule sees an empty value.
    internal fun getAutomaticSessionValue(context: Context, key: String): String? {
        return try {
            val data = AppUsageUtils.getEnhancedSessionData(context)
            when (key) {
                "session_count" -> data["session_count"]
                "screen_view_count", "screens_count" -> data["screen_view_count"]
                "session_duration_minutes" -> data["session_duration_minutes"]
                "session_duration" -> data["session_duration_seconds"] // seconds, same key as the Configuration Manager
                "first_launch" -> ((data["session_count"]?.toIntOrNull() ?: 0) <= 1).toString()
                "current_screen", "currentScreen" -> currentScreenProvider?.invoke()
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    // ===== INSTALL TIME VALUES =====
    // Values come from PackageInfo.firstInstallTime. On failure an empty value is returned
    // (numeric rules then do not match) instead of a misleading "0".
    internal fun getInstallTimeValue(context: Context, unit: String): String {
        return try {
            val installInfo = AppUsageUtils.getAppInstallInfo(context)
            when (unit) {
                "days" -> installInfo["days_since_install"] ?: ""
                "hours" -> installInfo["hours_since_install"] ?: ""
                "minutes" -> installInfo["minutes_since_install"] ?: ""
                "millis", "install_timestamp" -> installInfo["app_install_time"] ?: ""
                else -> installInfo["days_since_install"] ?: ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    // ===== APP USAGE VALUES =====
    internal fun getAppUsageValue(context: Context, metric: String): String? {
        return try {
            when (metric) {
                "sessions" -> {
                    val sessionData = AppUsageUtils.getEnhancedSessionData(context)
                    sessionData["session_count"] ?: "0"
                }
                "last_session" -> {
                    // Minutes since the previous session; empty on the very first session
                    val sessionData = AppUsageUtils.getEnhancedSessionData(context)
                    sessionData["time_since_last_session_minutes"] ?: ""
                }
                "screen_views" -> {
                    val sessionData = AppUsageUtils.getEnhancedSessionData(context)
                    sessionData["screen_view_count"] ?: "0"
                }
                "app_usage_time" -> {
                    val usageData = AppUsageUtils.getAppUsageData(context)
                    // Not measured by the SDK; empty (unknown) rather than a misleading "0"
                    usageData["app_usage_time_minutes"] ?: ""
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    // ===== NUMERIC COMPARISON =====
    private fun compareNumericValues(
        actualValue: String,
        matchValue: String,
        comparator: (Double, Double) -> Boolean
    ): Boolean {
        return try {
            val actualNum = actualValue.toDoubleOrNull()
            val matchNum = matchValue.toDoubleOrNull()
            actualNum != null && matchNum != null && comparator(actualNum, matchNum)
        } catch (e: Exception) {
            false
        }
    }

    // Session value for SESSION rules and custom parameters:
    // values written with setSessionData() win, then the automatic session metrics.
    internal fun getSessionValue(context: Context, key: String): String? {
        return sessionStorage[key] ?: getAutomaticSessionValue(context, key)
    }

    // ===== SESSION TRACKING METHODS =====
    // A session is one app process. autoSetup() runs once per Activity, so count only the first call.
    @Volatile
    private var sessionTrackedInProcess = false

    @Synchronized
    fun trackSessionStart(context: Context) {
        if (sessionTrackedInProcess) return
        sessionTrackedInProcess = true

        val prefs = context.getSharedPreferences("survey_sdk_session", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val sessionCount = prefs.getInt("session_count", 0) + 1
        val previousSessionStart = prefs.getLong("session_start_time", 0L)
        prefs.edit()
            .putInt("session_count", sessionCount)
            .putLong("previous_session_time", previousSessionStart)
            .putLong("session_start_time", now)
            .putLong("last_session_time", now)
            .apply()

        Log.d("MobileSDK", "Session tracked: #$sessionCount")
    }

    fun trackScreenView(context: Context) {
        val prefs = context.getSharedPreferences("survey_sdk_session", Context.MODE_PRIVATE)
        val screenViews = prefs.getInt("screen_view_count", 0) + 1
        prefs.edit().putInt("screen_view_count", screenViews).apply()

        Log.d("MobileSDK", "Screen view tracked: #$screenViews")
    }

    fun resetSessionData(context: Context) {
        val prefs = context.getSharedPreferences("survey_sdk_session", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        sessionStorage.clear()
        sessionTrackedInProcess = false

        Log.d("MobileSDK", "Session data reset")
    }
}