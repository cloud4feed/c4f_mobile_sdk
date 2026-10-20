package com.c4f.mobileSDK

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.util.Log
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import org.json.JSONException 

class ConfigCacheManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "survey_config_cache"
        private const val KEY_CONFIG_JSON = "cached_config"
        private const val KEY_TIMESTAMP = "cache_timestamp"
        private const val KEY_CACHE_DURATION = "cache_duration"
        private const val KEY_PARAMS_HASH = "cached_params_hash"
        private const val KEY_FORMAT_VERSION = "cache_format_version"

        // Bump whenever the cached JSON layout changes. Caches written by an older layout
        // (e.g. without "status" / "buttonTriggerId") are discarded instead of being half-read.
        private const val CACHE_FORMAT_VERSION = 2
    }

    /**
     * Returns the string value, or null when the key is missing or holds a JSON null.
     * (org.json's optString(name, null) returns the text "null" for JSON null values.)
     */
    private fun JSONObject.optStringOrNull(name: String): String? =
        if (!has(name) || isNull(name)) null else optString(name)

    fun saveConfig(config: Config, params: Map<String, String> = emptyMap()) {
        try {
            val configJson = configToJson(config)
            val paramsHash = params.hashCode().toString()
            
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_CONFIG_JSON, configJson)
                .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                .putLong(KEY_CACHE_DURATION, config.cacheDurationHours)
                .putString(KEY_PARAMS_HASH, paramsHash)
                .putInt(KEY_FORMAT_VERSION, CACHE_FORMAT_VERSION)
                .apply()
            Log.d("ConfigCache", "Configuration cached successfully for params: $params")
        } catch (e: Exception) {
            Log.e("ConfigCache", "Failed to cache configuration: ${e.message}")
        }
    }

    fun getCachedConfig(params: Map<String, String> = emptyMap()): Config? {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val cachedJson = prefs.getString(KEY_CONFIG_JSON, null) ?: return null

            // Discard caches written by an older (incomplete) layout
            if (prefs.getInt(KEY_FORMAT_VERSION, 1) != CACHE_FORMAT_VERSION) {
                Log.d("ConfigCache", "Cache format changed, discarding old cache")
                clearCache()
                return null
            }

            val timestamp = prefs.getLong(KEY_TIMESTAMP, 0)
            val cachedParamsHash = prefs.getString(KEY_PARAMS_HASH, "")
            val currentParamsHash = params.hashCode().toString()
            
            // Check if cached config matches current parameters
            if (cachedParamsHash != currentParamsHash) {
                Log.d("ConfigCache", "⚠️ Cached config parameters don't match current parameters")
                Log.d("ConfigCache", "   • Cached hash: $cachedParamsHash")
                Log.d("ConfigCache", "   • Current hash: $currentParamsHash")
                clearCache()
                return null
            }
            
            val cacheDurationHours = prefs.getLong(KEY_CACHE_DURATION, 24L)
            val cacheAge = System.currentTimeMillis() - timestamp
            val cacheExpired = cacheAge > TimeUnit.HOURS.toMillis(cacheDurationHours)
            
            if (cacheExpired) {
                Log.d("ConfigCache", "Cache expired, age: ${cacheAge / 1000 / 60} minutes")
                clearCache()
                return null
            }
            
            val config = jsonToConfig(cachedJson)

            // If any cached survey failed to parse, treat it as a cache miss so the
            // caller fetches a fresh configuration instead of serving a partial/empty one.
            val expectedSurveyCount = JSONObject(cachedJson).optJSONArray("surveys")?.length() ?: 0
            if (config.surveys.size != expectedSurveyCount) {
                Log.w("ConfigCache", "Cached config parsed ${config.surveys.size}/$expectedSurveyCount surveys, discarding cache")
                clearCache()
                return null
            }

            Log.d("ConfigCache", "Using cached configuration for params: $params")
            return config
        } catch (e: Exception) {
            Log.e("ConfigCache", "Failed to load cached configuration: ${e.message}")
            return null
        }
    }

    fun clearCache() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        Log.d("ConfigCache", "Configuration cache cleared")
    }

    private fun configToJson(config: Config): String {
        val jsonObject = JSONObject()

        jsonObject.put("sdkVersion", config.sdkVersion)
        jsonObject.put("cacheDurationHours", config.cacheDurationHours)

        // Serialize surveys array
        val surveysArray = JSONArray()
        config.surveys.forEach { survey ->
            val surveyJson = JSONObject()

            // Basic survey info
            surveyJson.put("surveyId", survey.surveyId)
            surveyJson.put("surveyName", survey.surveyName)
            surveyJson.put("baseUrl", survey.baseUrl)
            surveyJson.put("status", survey.status)

            // Trigger Settings
            surveyJson.put("enableButtonTrigger", survey.enableButtonTrigger)
            surveyJson.put("enableScrollTrigger", survey.enableScrollTrigger)
            surveyJson.put("enableNavigationTrigger", survey.enableNavigationTrigger)
            surveyJson.put("enableAppLaunchTrigger", survey.enableAppLaunchTrigger)
            surveyJson.put("enableExitTrigger", survey.enableExitTrigger)
            surveyJson.put("enableTabChangeTrigger", survey.enableTabChangeTrigger)

            // Trigger Configuration
            surveyJson.putOpt("buttonTriggerId", survey.buttonTriggerId) // omitted when null

            val triggerScreensArray = JSONArray()
            survey.triggerScreens.forEach { screen -> triggerScreensArray.put(screen) }
            surveyJson.put("triggerScreens", triggerScreensArray)

            val triggerTabsArray = JSONArray()
            survey.triggerTabs.forEach { tab -> triggerTabsArray.put(tab) }
            surveyJson.put("triggerTabs", triggerTabsArray)

            surveyJson.put("timeDelay", survey.timeDelay)
            surveyJson.put("scrollThreshold", survey.scrollThreshold)
            surveyJson.put("triggerType", survey.triggerType)

            // Display Settings
            surveyJson.put("modalStyle", survey.modalStyle)
            surveyJson.put("animationType", survey.animationType)
            surveyJson.put("backgroundColor", survey.backgroundColor)

            // Targeting & Limits
            surveyJson.put("probability", survey.probability)
            surveyJson.put("maxShowsPerSession", survey.maxShowsPerSession)
            surveyJson.put("cooldownPeriod", survey.cooldownPeriod)
            surveyJson.put("triggerOnce", survey.triggerOnce)
            surveyJson.put("priority", survey.priority)

            // Data Collection
            surveyJson.put("collectDeviceId", survey.collectDeviceId)
            surveyJson.put("collectDeviceModel", survey.collectDeviceModel)
            surveyJson.put("collectLocation", survey.collectLocation)
            surveyJson.put("collectAppUsage", survey.collectAppUsage)

            // Custom Params
            val customParamsArray = JSONArray()
            survey.customParams.forEach { param ->
                val paramJson = JSONObject()
                paramJson.put("name", param.name)
                paramJson.put("source", param.source.name)
                // putOpt omits a null value so it stays absent after the round trip
                paramJson.putOpt("key", param.key)
                customParamsArray.put(paramJson)
            }
            surveyJson.put("customParams", customParamsArray)

            // Exclusion Rules
            val exclusionRulesArray = JSONArray()
            survey.exclusionRules.forEach { rule ->
                val ruleJson = JSONObject()
                ruleJson.put("name", rule.name)
                ruleJson.put("source", rule.source.name)
                ruleJson.putOpt("key", rule.key)
                ruleJson.putOpt("value", rule.value)
                ruleJson.put("operator", rule.operator.id) // Store as integer ID
                ruleJson.put("matchValue", rule.matchValue)
                ruleJson.put("caseSensitive", rule.caseSensitive)
                exclusionRulesArray.put(ruleJson)
            }
            surveyJson.put("exclusionRules", exclusionRulesArray)

            surveysArray.put(surveyJson)
        }
        jsonObject.put("surveys", surveysArray)

        return jsonObject.toString()
    }

    private fun jsonToConfig(jsonString: String): Config {
        return try {
            val json = JSONObject(jsonString)
            
            // Determine which JSON object contains our config
            val configJson = when {
                // Case 1: Response from backend (has data wrapper)
                json.has("data") && !json.isNull("data") -> {
                    Log.d("ConfigCacheManager", "📦 Parsing from backend response (data wrapper)")
                    json.getJSONObject("data")
                }
                // Case 2: Already parsed config (direct structure)
                json.has("surveys") -> {
                    Log.d("ConfigCacheManager", "📦 Parsing cached config (direct structure)")
                    json
                }
                // Case 3: Invalid structure - return empty config
                else -> {
                    Log.e("ConfigCacheManager", "❌ Invalid config structure in cache")
                    Log.e("ConfigCacheManager", "❌ JSON: ${jsonString.take(500)}")
                    // Return empty config instead of throwing
                    return DefaultConfig.EMPTY_CONFIG
                }
            }
            
            val surveysArray = configJson.optJSONArray("surveys")
            val surveys = mutableListOf<SurveyConfig>()

            if (surveysArray != null) {
                Log.d("ConfigCacheManager", "📊 Found ${surveysArray.length()} surveys")
                for (i in 0 until surveysArray.length()) {
                    try {
                        val surveyJson = surveysArray.getJSONObject(i)
                        surveys.add(parseSurveyConfigFromJson(surveyJson))
                    } catch (e: Exception) {
                        Log.e("ConfigCacheManager", "Error parsing survey at index $i: ${e.message}")
                    }
                }
            } else {
                Log.w("ConfigCacheManager", "⚠️ No surveys found in cached config")
            }

            Config(
                sdkVersion = configJson.optString("sdkVersion", "2.0.0"),
                cacheDurationHours = configJson.optLong("cacheDurationHours", 24L),
                surveys = surveys
            )
        } catch (e: Exception) {
            Log.e("ConfigCacheManager", "Error parsing config JSON: ${e.message}")
            Log.e("ConfigCacheManager", "❌ Raw JSON that failed: ${jsonString.take(500)}")
            DefaultConfig.EMPTY_CONFIG
        }
    }

    private fun parseSurveyConfigFromJson(surveyJson: JSONObject): SurveyConfig {
        // Handle priority which can be String or Int
        val priorityValue = surveyJson.opt("priority")
        val priority = when (priorityValue) {
            is String -> priorityValue.toIntOrNull() ?: SDKConstants.DEFAULT_PRIORITY
            is Int -> priorityValue
            is Double -> priorityValue.toInt()
            else -> SDKConstants.DEFAULT_PRIORITY
        }

        // Get surveyId from backend, generate random if empty/null
            val backendSurveyId = surveyJson.optString("surveyId", "")
            val surveyId = if (backendSurveyId.isNotEmpty()) {
                backendSurveyId  // Use backend ID if provided
            } else {
                // Generate random ID
                "survey_${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}"
            }
        
        return SurveyConfig(
            surveyId = surveyJson.optString("surveyId", ""), // Add surveyId field
            surveyName = surveyJson.optString("surveyName", ""), // Add surveyName field
            baseUrl = surveyJson.optString("baseUrl", ""),
            status = surveyJson.optBoolean("status", true),
            enableButtonTrigger = surveyJson.optBoolean("enableButtonTrigger", false),
            enableScrollTrigger = surveyJson.optBoolean("enableScrollTrigger", false),
            enableNavigationTrigger = surveyJson.optBoolean("enableNavigationTrigger", false),
            enableAppLaunchTrigger = surveyJson.optBoolean("enableAppLaunchTrigger", false),
            enableExitTrigger = surveyJson.optBoolean("enableExitTrigger", false),
            enableTabChangeTrigger = surveyJson.optBoolean("enableTabChangeTrigger", false),
            buttonTriggerId = surveyJson.optStringOrNull("buttonTriggerId")?.takeIf { it.isNotEmpty() },
            triggerScreens = surveyJson.optJSONArray("triggerScreens")?.let { array ->
                (0 until array.length()).mapNotNull { 
                    if (!array.isNull(it)) array.getString(it) else null 
                }.toSet()
            } ?: emptySet(),
            triggerTabs = surveyJson.optJSONArray("triggerTabs")?.let { array ->
                (0 until array.length()).mapNotNull { 
                    if (!array.isNull(it)) array.getString(it) else null 
                }.toSet()
            } ?: emptySet(),
            timeDelay = surveyJson.optLong("timeDelay", 0L),
            scrollThreshold = surveyJson.optInt("scrollThreshold", 0),
            triggerType = surveyJson.optString("triggerType", "instant"),
            modalStyle = surveyJson.optString("modalStyle", "full_screen"),
            animationType = surveyJson.optString("animationType", "slide_up"),
            backgroundColor = surveyJson.optString("backgroundColor", "#FFFFFF"),
            probability = surveyJson.optDouble("probability", 1.0),
            maxShowsPerSession = surveyJson.optInt("maxShowsPerSession", 0),
            cooldownPeriod = surveyJson.optLong("cooldownPeriod", 0L),
            triggerOnce = surveyJson.optBoolean("triggerOnce", false),
            priority = priority, // Use the parsed priority
            collectDeviceId = surveyJson.optBoolean("collectDeviceId", false),
            collectDeviceModel = surveyJson.optBoolean("collectDeviceModel", false),
            collectLocation = surveyJson.optBoolean("collectLocation", false),
            collectAppUsage = surveyJson.optBoolean("collectAppUsage", false),
            customParams = parseCustomParams(surveyJson.optJSONArray("customParams")),
            exclusionRules = parseExclusionRules(surveyJson.optJSONArray("exclusionRules"))
        )
    }

    private fun parseCustomParams(jsonArray: JSONArray?): List<CustomParam> {
        return jsonArray?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                try {
                    val paramJson = array.getJSONObject(index)
                    CustomParam(
                        name = paramJson.getString("name"),
                        source = ParamSource.valueOf(paramJson.getString("source").uppercase()),
                        key = paramJson.optStringOrNull("key")
                    )
                } catch (e: Exception) {
                    Log.e("ConfigCacheManager", "Error parsing custom param: ${e.message}")
                    null
                }
            }
        } ?: emptyList()
    }

    private fun parseExclusionRules(jsonArray: JSONArray?): List<ExclusionRule> {
        return jsonArray?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                try {
                    val ruleJson = array.getJSONObject(index)

                    // Handle operator as both integer and string
                    val operator = if (ruleJson.has("operator")) {
                        when (val operatorValue = ruleJson.get("operator")) {
                            is Int -> ExclusionOperator.fromId(operatorValue)
                            is String -> ExclusionOperator.fromName(operatorValue)
                            else -> ExclusionOperator.EQUALS
                        }
                    } else {
                        ExclusionOperator.EQUALS
                    }

                    ExclusionRule(
                        // ✅ Make name optional - generate if not provided
                        name = ruleJson.optString("name", "rule_$index"),
                        source = ExclusionSource.valueOf(ruleJson.optString("source", "STORAGE")),
                        key = ruleJson.optStringOrNull("key"),
                        value = ruleJson.optStringOrNull("value"),
                        operator = operator,
                        matchValue = ruleJson.optString("matchValue", ""),
                        caseSensitive = ruleJson.optBoolean("caseSensitive", false)
                    )
                } catch (e: Exception) {
                    Log.e("SurveyApiService", "Error parsing exclusion rule: ${e.message}")
                    null
                }
            }
        } ?: emptyList()
    }
}