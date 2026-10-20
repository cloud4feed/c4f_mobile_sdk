package com.c4f.mobileSDK

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.navigation.NavController
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.mutableMapOf

// ===== PUBLIC CONFIGURATION DATA CLASSES =====
data class Config(
    val sdkVersion: String = "2.0.0",
    val cacheDurationHours: Long = 24L,
    val surveys: List<SurveyConfig> = emptyList()
)

data class SurveyConfig(
    // Basic Survey Identity
    val surveyId: String = "",
    val surveyName: String = "",
    val baseUrl: String = "",
    val status: Boolean = true,
    // Trigger Settings
    val enableButtonTrigger: Boolean = false,
    val enableScrollTrigger: Boolean = false,
    val enableNavigationTrigger: Boolean = false,
    val enableAppLaunchTrigger: Boolean = false,
    val enableExitTrigger: Boolean = false,
    val enableTabChangeTrigger: Boolean = false,

    val buttonTriggerId: String? = null, // NEW: Custom button ID (optional)

    // Trigger Configuration
    val triggerScreens: Set<String> = emptySet(),
    val triggerTabs: Set<String> = emptySet(),
    val timeDelay: Long = 0L,
    val scrollThreshold: Int = 0,
    val triggerType: String = "instant",

    // Display Settings
    val modalStyle: String = "full_screen",
    val animationType: String = "slide_up",
    val backgroundColor: String = "#FFFFFF",

    // Targeting & Limits
    val exclusionRules: List<ExclusionRule> = emptyList(),
    val probability: Double = 1.0,
    val maxShowsPerSession: Int = 0,
    val cooldownPeriod: Long = 0L,
    val triggerOnce: Boolean = false,
    val priority: Int = 1,

    // Data Collection
    val collectDeviceId: Boolean = false,
    val collectDeviceModel: Boolean = false,
    val collectLocation: Boolean = false,
    val collectAppUsage: Boolean = false,
    val customParams: List<CustomParam> = emptyList()
)

data class CustomParam(
    val name: String,
    val source: ParamSource,
    val key: String? = null
)

enum class ParamSource {
    STORAGE, DEVICE, URL, TIMESTAMP, SESSION, INSTALL_TIME, APP_USAGE
}

data class ExclusionRule(
    val name: String,
    val source: ExclusionSource,
    val key: String? = null,
    val value: String? = null,
    val operator: ExclusionOperator,
    val matchValue: String,
    val caseSensitive: Boolean = false
)

enum class ExclusionSource {
    STORAGE, DEVICE, URL, TIMESTAMP, SESSION, INSTALL_TIME, APP_USAGE
}

enum class ExclusionOperator(val id: Int) {
    LESS_THAN(1),
    LESS_OR_EQUAL(2),
    IN(3),
    NOT_IN(4),
    GREATER_THAN(5),
    GREATER_OR_EQUAL(6),
    EMPTY(7),
    NOT_EMPTY(8),
    CONTAINS(9),
    NOT_CONTAINS(10),
    STARTS_WITH(11), // "StartWith"
    ENDS_WITH(12),   // "LastWith"
    LENGTH(13),      // "Lenghth"
    EQUALS(14),      // "EqualTo"
    NOT_EQUALS(15);  // "NotEqual"

    companion object {
        fun fromId(id: Int): ExclusionOperator {
            return values().find { it.id == id } ?: EQUALS // Default to EQUALS if unknown
        }
        
        fun fromName(name: String): ExclusionOperator {
            return try {
                valueOf(name)
            } catch (e: Exception) {
                EQUALS
            }
        }
    }
}

// ==================== MAIN INITIALIZATION METHODS ====================

/**
 * Unified initialization method supporting multiple parameter styles:
 * 
 * 1. Simple initialization (just API key):
 *    MobileSDK.initialize(this, "test-api-key")
 * 
 * 2. With storage parameters (look up values from storage):
 *    MobileSDK.initialize(this, "test-api-key", "userID", "rank")
 * 
 * 3. With mixed parameters (direct values + storage lookups):
 *    MobileSDK.initialize(this, "test-api-key",
 *        "userID" to "123",
 *        "rank" to "chef",
 *        "branch" // Will be looked up from storage
 *    )
 */
// ====================================================================
// MAIN SURVEY SDK CLASS
// ====================================================================
class MobileSDK private constructor(
    private val context: Context,
    internal val apiKey: String,
    var customParams: Map<String, String> = emptyMap() 
    ) {
    
    companion object {
        @Volatile
        private var instance: MobileSDK? = null

        // ===== SCENARIO 1: Simple Initialization =====
        fun initialize(context: Context, apiKey: String): MobileSDK {
            Log.d("MobileSDK", "🔧 Simple initialization (no parameters)")
            return getOrCreateInstance(context, apiKey, emptyMap())
        }
        
        // ===== SCENARIO 2: Storage Parameters =====
        fun initialize(context: Context, apiKey: String, vararg paramNames: String): MobileSDK {
            Log.d("MobileSDK", "🔧 Initializing with ${paramNames.size} storage parameters")
            val params = mutableMapOf<String, String>()
            
            paramNames.forEach { paramName ->
                val value = StorageUtils.findSpecificData(context, paramName)
                if (value != null) {
                    params[paramName] = value
                    Log.d("MobileSDK", "   ✅ Found '$paramName' in storage: $value")
                } else {
                    Log.w("MobileSDK", "   ⚠️ '$paramName' not found in storage")
                }
            }
            
            return getOrCreateInstance(context, apiKey, params)
        }
        
        // ===== SCENARIO 3: Direct Key-Value Pairs =====
        fun initialize(context: Context, apiKey: String, vararg params: Pair<String, String>): MobileSDK {
            Log.d("MobileSDK", "🔧 Initializing with ${params.size} direct parameters")
            val paramMap = params.associate { it.first to it.second }
            
            paramMap.forEach { (key, value) ->
                Log.d("MobileSDK", "   ✅ Direct param: $key = $value")
            }
            
            return getOrCreateInstance(context, apiKey, paramMap)
        }
        
        // ===== SCENARIO 4: Mixed Parameters =====
        fun initialize(context: Context, apiKey: String, vararg params: Any): MobileSDK {
            Log.d("MobileSDK", "🔧 Initializing with ${params.size} mixed parameters")
            val paramMap = mutableMapOf<String, String>()
            
            params.forEach { param ->
                when (param) {
                    is String -> {
                        // Look up from storage
                        val value = StorageUtils.findSpecificData(context, param)
                        if (value != null) {
                            paramMap[param] = value
                            Log.d("MobileSDK", "   ✅ From storage: $param = $value")
                        } else {
                            Log.w("MobileSDK", "   ⚠️ '$param' not found in storage")
                        }
                    }
                    is Pair<*, *> -> {
                        // Direct value
                        (param.first as? String)?.let { key ->
                            (param.second as? String)?.let { value ->
                                paramMap[key] = value
                                Log.d("MobileSDK", "   ✅ Direct param: $key = $value")
                            }
                        }
                    }
                    else -> {
                        Log.e("MobileSDK", "   ❌ Invalid parameter type: ${param.javaClass}")
                    }
                }
            }
            
            return getOrCreateInstance(context, apiKey, paramMap)
        }
        
        private fun getOrCreateInstance(
            context: Context,
            apiKey: String,
            params: Map<String, String>
            ): MobileSDK {
            Log.d("MobileSDK", "=== INSTANCE CREATION ===")
            Log.d("MobileSDK", "   • API Key: ${apiKey.take(5)}...")
            Log.d("MobileSDK", "   • Parameters: ${params.size} items")
            
            val currentInstance = instance
            
            // Check if we need to create a new instance
            val needsNewInstance = when {
                currentInstance == null -> {
                    Log.d("MobileSDK", "   • No existing instance")
                    true
                }
                currentInstance.apiKey != apiKey -> {
                    Log.d("MobileSDK", "🔄 Different API key detected, creating new instance")
                    true
                }
                else -> {
                    Log.d("MobileSDK", "⚡ Same API key - will update existing instance")
                    false
                }
            }
            
            // Clean up old instance if API key changed
            if (currentInstance != null && currentInstance.apiKey != apiKey) {
                Log.d("MobileSDK", "🧹 Cleaning up old instance (different API key)...")
                try {
                    currentInstance.cleanup()
                } catch (e: Exception) {
                    Log.e("MobileSDK", "❌ Error during cleanup: ${e.message}")
                }
            }
            
            return if (!needsNewInstance && currentInstance != null) {
                // Update parameters in existing instance
                synchronized(this) {
                    currentInstance.updateParameters(params)
                    currentInstance
                }
            } else {
                synchronized(this) {
                    // Double-check in synchronized block
                    instance?.let { existing ->
                        if (existing.apiKey == apiKey) {
                            // Another thread might have created it
                            existing.updateParameters(params)
                            return existing
                        }
                    }
                    
                    // Create new instance
                    MobileSDK(context.applicationContext, apiKey, params).also { sdk ->
                        instance = sdk
                        sdk.initialized = true
                        
                        Log.d("MobileSDK", if (params.isEmpty()) {
                            "✅ SDK initialized WITHOUT parameters"
                        } else {
                            "✅ SDK initialized WITH ${params.size} parameters"
                        })
                        
                        // Load fallback config first
                        sdk.loadFallbackConfig()
                        
                        // Auto-fetch configuration
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            sdk.loadConfigurationAutomatically()
                        }
                    }
                }
            }
        }
        
        // ===== HELPER METHODS =====
        fun getCurrentParameters(): Map<String, String> {
            return try {
                getInstance().customParams
            } catch (e: IllegalStateException) {
                emptyMap()
            }
        }
        
        fun getInstance(): MobileSDK {
            return instance ?: throw IllegalStateException(SDKConstants.ERROR_NOT_INITIALIZED)
        }

        /**
         * Initialize SDK with parameters for React Native bridge
         * This is the ONLY method bridges should call
         */
        @JvmStatic
        fun initializeWithBridgeParams(
            context: Context,
            apiKey: String,
            vararg params: Any
        ): MobileSDK {
            return when {
                params.isEmpty() -> initialize(context, apiKey)
                else -> initialize(context, apiKey, *params)
            }
        }
        
        // Add this method for UniversalMobileSDK
        fun forceReinitialize(context: Context): Boolean {
            return try {
                instance?.cleanup()
                instance = null
                Log.d("MobileSDK", "🔄 Force reinitialization completed")
                true
            } catch (e: Exception) {
                Log.e("MobileSDK", "❌ Force reinitialization failed: ${e.message}")
                false
            }
        }
    }
    
    // ==================== PROPERTIES ====================
    private lateinit var config: Config
    private var configurationLoaded = false
    private val surveyShownCount = mutableMapOf<String, Int>()
    private val lastSurveyTime = mutableMapOf<String, Long>()
    private val triggeredScreens = mutableMapOf<String, MutableSet<String>>()
    private val triggeredTabs = mutableMapOf<String, MutableSet<String>>()
    private val triggeredExits = mutableMapOf<String, MutableSet<String>>()
    private val screenTimers = mutableMapOf<String, Long>()
    private var activityLifecycleCallbacks: Application.ActivityLifecycleCallbacks? = null
    private val delayExecutors = mutableMapOf<String, ActivitySafeExecutor>()
    private var initialized = false
    private val processedViews = java.util.WeakHashMap<View, Boolean>()
    private val strongViewReferences = mutableListOf<View>()
    private val scrollViewsWithListeners = mutableSetOf<Int>()
    private var previousScreen: String? = null
    private val viewsWithScrollListeners = java.util.WeakHashMap<View, Boolean>()
    private val surveyTriggerTimestamps = ConcurrentHashMap<String, Long>()
    private var currentContextName: String? = null
    private var lastActivity: Activity? = null
    private var processLifecycleSetup = false
    private var lastPauseTime: Long = 0
    private var lastStopTime: Long = 0
    private val activeActivities = mutableSetOf<String>()
    private var lastSurveyClosedTime: Long = 0
    private val SURVEY_CLOSE_COOLDOWN_MS = 2000L
    private var lastTriggeredTabName: String? = null
    private var lastTriggeredTabTime: Long = 0
    private var lastScrollTriggerTime: Long = 0
    private val SCROLL_COOLDOWN_MS = 3000L
    private var lastNavigationTriggerTime: Long = 0
    private val NAVIGATION_TRIGGER_DEBOUNCE_MS = 300L
    private val navigationListenersSet = mutableSetOf<String>()
    private var navigationSafetyEnabled = false
    private var appLaunchTriggerSetup = false
    private val pendingNavigationTriggers = mutableMapOf<String, Pair<Long, Activity>>()
    private var isHybridMode = false
    // Survey queue system
    private val surveyQueue = mutableListOf<Pair<Activity, SurveyConfig>>()
    private var isShowingSurvey = false
    private val queueLock = Any()
    private var lastStringIdTriggerTime: Long = 0
    // Services
    private val apiService: SurveyApiService by lazy { SurveyApiService(apiKey) }
    private val configCacheManager: ConfigCacheManager by lazy { ConfigCacheManager(context) }

      // ==================== BACKWARD COMPATIBILITY GETTERS ====================
        // For methods that still use these old properties
        internal val configuredUserIdKey: String? 
            get() = customParams.entries.firstOrNull()?.key
        
        internal val configuredUserIdValue: String? 
            get() = customParams.entries.firstOrNull()?.value
        
        // ==================== INIT BLOCK ====================
        init {
            Log.d("MobileSDK", "📦 MobileSDK instance created")
            Log.d("MobileSDK", "   • API Key: ${apiKey.take(5)}...")
            Log.d("MobileSDK", "   • Parameters: ${customParams.size} items")
            
            if (customParams.isNotEmpty()) {
                customParams.forEach { (key, value) ->
                    Log.d("MobileSDK", "     - $key = $value")
                }
            }
        }
    

    fun updateParameters(newParams: Map<String, String>) {
        if (customParams == newParams) {
            Log.d("MobileSDK", "⚡ Parameters unchanged, skipping update")
            return
        }
        
        Log.d("MobileSDK", "🔄 Updating parameters")
        Log.d("MobileSDK", "   • Old: ${customParams.size} items - $customParams")
        Log.d("MobileSDK", "   • New: ${newParams.size} items - $newParams")
        
        customParams = newParams
        
        // Clear configuration to force reload with new parameters
        configurationLoaded = false
        resetSurveyTracking() // Clear any survey counts/triggers
        
        Log.d("MobileSDK", "✅ Parameters updated, reloading configuration...")
        
        // Reload configuration with new parameters
        loadConfigurationAutomatically()
    }

    // ====================================================================
    // PUBLIC API - INITIALIZATION & SETUP
    // ====================================================================
    fun enableNavigationSafety(): MobileSDK {
        navigationSafetyEnabled = true
        Log.d("MobileSDK", "🚦 Navigation Safety ENABLED")
        return this
    }

    fun autoSetup(activity: Activity): MobileSDK {
        Log.d("MobileSDK", "🚀 Starting autoSetup on ${activity::class.simpleName}")
        lastActivity = activity
        ExclusionRuleEvaluator.currentScreenProvider = { currentContextName }

        if (!processLifecycleSetup) {
            setupProcessLifecycle()
            processLifecycleSetup = true
        }

        trackAppStart(activity)

        try {
            autoDetectNavigationComponent(activity)
        } catch (e: Exception) {
            Log.d("MobileSDK", "⚠️ Navigation Component auto-detection failed: ${e.message}")
        }

        // NEW: Wait for configuration before setting up triggers
        waitForConfigurationThenSetup(activity)
        
        return this
    }

    private fun waitForConfigurationThenSetup(activity: Activity) {
        val maxWaitTime = 10000L // 10 seconds max
        val startTime = System.currentTimeMillis()
        
        Log.d("MobileSDK", "⏳ Waiting for configuration before setting up triggers...")
        
        val configCheckRunnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - startTime
                
                if (configurationLoaded) {
                    Log.d("MobileSDK", "✅ Configuration loaded (took ${elapsed}ms)")
                    setupAllTriggers(activity)
                } else if (elapsed < maxWaitTime && !activity.isFinishing && !activity.isDestroyed) {
                    // Keep checking every 500ms
                    activity.window.decorView.postDelayed(this, 500)
                } else if (elapsed >= maxWaitTime) {
                    Log.w("MobileSDK", "⚠️ Configuration load timeout after ${elapsed}ms")
                    // Setup with whatever we have (empty config)
                    if (!configurationLoaded) {
                        Log.d("MobileSDK", "🔄 Loading fallback config due to timeout")
                        loadFallbackConfig()
                        setupAllTriggers(activity)
                    }
                }
            }
        }
        
        if (configurationLoaded) {
            Log.d("MobileSDK", "⚡ Configuration already loaded, setting up triggers immediately")
            setupAllTriggers(activity)
        } else {
            activity.window.decorView.post(configCheckRunnable)
        }
    }

    private fun setupAllTriggers(activity: Activity) {
        Log.d("MobileSDK", "🎯 Setting up ALL triggers with ${config.surveys.size} surveys")
        
        // Initialize tracking for each survey
        config.surveys.forEach { survey ->
            surveyShownCount[survey.surveyId] = 0
            lastSurveyTime[survey.surveyId] = 0
            triggeredScreens[survey.surveyId] = mutableSetOf()
            triggeredTabs[survey.surveyId] = mutableSetOf()
            triggeredExits[survey.surveyId] = mutableSetOf()
        }

        activity.window.decorView.post {
            if (activity.isFinishing || activity.isDestroyed) {
                return@post
            }
            
            Handler(Looper.getMainLooper()).postDelayed({
                
                // 🛡️ HYBRID MODE CHECK: Only run these in pure Android apps!
                if (!isHybridMode) {
                    if (config.surveys.any { it.enableNavigationTrigger || it.enableTabChangeTrigger }) {
                        autoDetectNavigation(activity)
                        autoDetectNavigationComponent(activity)
                    }
                    
                    startGlobalViewScanning(activity)
                    
                    Handler(Looper.getMainLooper()).postDelayed({
                        setupAutoScreenTracking(activity)
                    }, 400)
                    
                    Handler(Looper.getMainLooper()).postDelayed({
                        setupSmartButtonDetection(activity)
                    }, 600)
                    
                    if (config.surveys.any { it.enableTabChangeTrigger }) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            setupTabChangeTrigger(activity)
                        }, 800)
                    }
                } else {
                    Log.d("MobileSDK", "⚛️ Hybrid Mode: Skipping Native UI Scanners")
                }

                // ✅ ALWAYS RUN THESE (Both pure Android & React Native need these)
                Handler(Looper.getMainLooper()).postDelayed({
                    setupScrollTrigger(activity)
                    setupScrollViewObserver(activity)
                }, 200)

                Handler(Looper.getMainLooper()).postDelayed({
                    setupAppLaunchTrigger(activity)
                }, 400)
                
                if (config.surveys.any { it.enableExitTrigger }) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        setupActivityLifecycle(activity)
                    }, 1000)
                }

                trackScreenView(activity)
                Log.d("MobileSDK", "✅ All triggers setup completed")
                
            }, 500)
        }

        setupViewTreeObserver(activity)
    }

    fun autoSetupSafe(activity: Activity): MobileSDK {
        Log.d("MobileSDK", "🛡️ Starting SAFE autoSetup")
        navigationSafetyEnabled = true
        return autoSetup(activity)
    }

    @Deprecated("Use autoSetupSafe() for apps with navigation, or enableNavigationSafety().autoSetup()")
    fun autoSetupFull(activity: Activity): MobileSDK {
        navigationSafetyEnabled = false
        return autoSetup(activity)
    }    

    // ====================================================================
    // PUBLIC API - SURVEY DISPLAY
    // ====================================================================
    fun showSurvey(activity: Activity) {
        showSurveyInternal(activity, null)
    }

    fun showSurveyById(activity: Activity, surveyId: String) {
        val survey = config.surveys.find { it.surveyId == surveyId }
        if (survey != null) {
            showSurveyInternal(activity, survey)
        } else {
            Log.e("MobileSDK", "Survey not found: $surveyId")
        }
    }

    fun triggerButtonByStringId(incomingButtonId: String, activity: Activity) {

        // 🛡️ THE FIX: If config is downloading, wait 1 second and retry the tap!
        if (!configurationLoaded) {
            Log.d("MobileSDK", "⏳ Config loading... retrying button tap in 1s")
            activity.window.decorView.postDelayed({
                if (!activity.isFinishing && !activity.isDestroyed) {
                    triggerButtonByStringId(incomingButtonId, activity)
                }
            }, 1000)
            return
        }
        
        // 🛡️ THE FIX: Ignore duplicate calls within 500ms
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastStringIdTriggerTime < 500) {
            Log.d("MobileSDK", "⚡ Debouncing rapid button trigger for: $incomingButtonId")
            return
        }
        lastStringIdTriggerTime = currentTime

        Log.d("MobileSDK", "RN: Bridge trigger request for ID: '$incomingButtonId'")

        // Scenario 1: Specific ID (Custom Defined ID)
        val specificSurveys = config.surveys.filter {
            it.enableButtonTrigger && it.buttonTriggerId == incomingButtonId
        }

        if (specificSurveys.isNotEmpty()) {
            val survey = specificSurveys.maxByOrNull { it.priority }!!
            Log.d("MobileSDK", "RN: ✅ Found specific survey match: ${survey.surveyId}")

            if (canShowSurvey(survey)) {
                showSingleSurvey(activity, survey)
            } else {
                Log.d("MobileSDK", "RN: ⚠️ Survey matched but rules prevented showing.")
            }
            return
        }

        // Scenario 2: Predefined ID (General Defined ID)
        if (SDKConstants.PREDEFINED_BUTTON_IDS.contains(incomingButtonId)) {
            Log.d("MobileSDK", "RN: ID matches Predefined list. Looking for generic survey...")

            val genericSurveys = config.surveys.filter {
                it.enableButtonTrigger && it.buttonTriggerId.isNullOrEmpty()
            }

            if (genericSurveys.isNotEmpty()) {
                val bestSurvey = findHighestPrioritySurvey(genericSurveys)
                Log.d("MobileSDK", "RN: ✅ Found generic survey: ${bestSurvey.surveyId}")

                if (canShowSurvey(bestSurvey)) {
                    showSingleSurvey(activity, bestSurvey)
                }
            } else {
                Log.w("MobileSDK", "RN: Predefined ID match, but no Generic Survey configured.")
            }
        } else {
            Log.w("MobileSDK", "RN: No match found for ID: '$incomingButtonId'")
        }
    }

    fun triggerScrollManual(activity: Activity, scrollY: Int = 500) {
        Log.d("MobileSDK", "RN: Manual scroll trigger check (y=$scrollY)")

        val matchingSurveys = config.surveys.filter { survey ->
            val meetsThreshold = scrollY >= survey.scrollThreshold
            val canShow = canShowSurvey(survey)
            survey.enableScrollTrigger && meetsThreshold && canShow
        }

        if (matchingSurveys.isNotEmpty()) {
            val bestSurvey = findHighestPrioritySurvey(matchingSurveys)
            Log.d("MobileSDK", "RN: Triggering scroll survey: ${bestSurvey.surveyId}")
            showSingleSurvey(activity, bestSurvey)
        }
    }

    // ====================================================================
    // PUBLIC API - TRIGGER MANAGEMENT
    // ====================================================================

    fun enableHybridMode(): MobileSDK {
        isHybridMode = true
        Log.d("MobileSDK", "⚛️ Hybrid Mode ENABLED - Disabling native UI scanners")
        return this
    }

    fun setupButtonTrigger(button: View, activity: Activity, surveyId: String? = null) {
        val surveysToUse = if (surveyId != null) {
            config.surveys.filter { it.surveyId == surveyId && it.enableButtonTrigger }
        } else {
            val availableSurveys = config.surveys.filter { it.enableButtonTrigger }
            if (availableSurveys.isNotEmpty()) {
                listOf(availableSurveys.maxByOrNull { it.priority }!!)
            } else {
                emptyList()
            }
        }

        if (surveysToUse.isEmpty()) {
            Log.w("MobileSDK", "❌ No surveys found for button trigger")
            return
        }

        val survey = surveysToUse.first()
        setupSingleButtonTrigger(button, activity, survey)
    }

    fun setupButtonTrigger(buttonId: Int, activity: Activity, surveyId: String? = null) {
        try {
            val button = activity.findViewById<View>(buttonId)
            if (button == null) {
                Log.e("MobileSDK", "❌ Button not found with ID: $buttonId")
                return
            }
            setupButtonTrigger(button, activity, surveyId)
        } catch (e: Exception) {
            Log.e("MobileSDK", "❌ setupButtonTrigger failed for ID $buttonId: ${e.message}")
        }
    }

    fun triggerByNavigation(screenName: String, activity: Activity) {
        Log.d("MobileSDK", "📍 Navigation trigger called: $screenName")
        
        if (!configurationLoaded) {
            Log.d("MobileSDK", "⏳ Config not loaded, delaying navigation trigger for: $screenName")
            
            // Store and retry after delay
            activity.window.decorView.postDelayed({
                if (!activity.isFinishing && !activity.isDestroyed) {
                    Log.d("MobileSDK", "🔄 Retrying navigation trigger for: $screenName")
                    triggerByNavigation(screenName, activity)
                }
            }, 1000)
            return
        }
        
        // Track for exit triggers (keep this running)
        trackScreenChangeForExit(screenName, activity)  
        
        // Skip if screen contains "survey"
        if (screenName.contains("survey", ignoreCase = true)) {
            Log.d("MobileSDK", "🔄 Skipping survey screen: $screenName")
            return
        }
        
        val currentTime = System.currentTimeMillis()
        val debounced = currentTime - lastNavigationTriggerTime < NAVIGATION_TRIGGER_DEBOUNCE_MS
        
        if (activity.isFinishing) {
            Log.d("MobileSDK", "⚠️ Activity is finishing, skipping navigation trigger")
            return
        }
        
        // ORIGINAL LOGIC (only runs after config is loaded)
        val matchingSurveys = config.surveys.filter { survey ->
            val isEnabled = survey.enableNavigationTrigger
            val matchesScreen = survey.triggerScreens.isEmpty() ||
                    survey.triggerScreens.any { triggerScreen ->
                        screenName.contains(triggerScreen, ignoreCase = true) ||
                                triggerScreen.contains(screenName, ignoreCase = true)
                    }
            val notTriggeredOnce = !(survey.triggerOnce &&
                    triggeredScreens[survey.surveyId]?.contains(screenName) == true)

            val canShowNow = canShowSurvey(survey)

            isEnabled && matchesScreen && notTriggeredOnce && canShowNow
        }

        Log.d("MobileSDK", "🔍 Found ${matchingSurveys.size} navigation surveys for: $screenName")
        matchingSurveys.forEach { survey ->
            Log.d("MobileSDK", "   • ${survey.surveyId}: triggerScreens=${survey.triggerScreens}")
        }

        if (matchingSurveys.isEmpty()) {
            Log.d("MobileSDK", "📭 No navigation surveys match screen: $screenName")
            return
        }

        if (debounced) {
            Log.d("MobileSDK", "⚠️ Navigation trigger debounced for: $screenName")
            return
        }

        lastNavigationTriggerTime = currentTime
        screenTimers[screenName] = currentTime

        matchingSurveys.forEach { survey ->
            Log.d("MobileSDK", "🎯 Processing navigation survey: ${survey.surveyId}")

            if (survey.triggerType == "delayed" && survey.timeDelay > 0) {
                Log.d("MobileSDK", "⏰ Setting up delayed trigger (${survey.timeDelay}ms)")
                setupScreenTimeTrigger(screenName, activity, survey)
            } else {
                if (canShowSurvey(survey)) {
                    Log.d("MobileSDK", "🚀 Immediately showing navigation survey")
                    triggeredScreens[survey.surveyId]?.add(screenName)
                    showSingleSurvey(activity, survey)
                } else {
                    Log.d("MobileSDK", "❌ Cannot show survey ${survey.surveyId} - conditions not met")
                    debugSurveyConditions(survey)
                }
            }
        }
    }

    fun retryPendingNavigationTriggers() {
        Log.d("MobileSDK", "🔄 Retrying pending navigation triggers")
        
        val currentTime = System.currentTimeMillis()
        val entriesToRemove = mutableListOf<String>()
        
        pendingNavigationTriggers.forEach { (screenName, pair) ->
            val (timestamp, activity) = pair
            
            // Remove if older than 10 seconds
            if (currentTime - timestamp > 10000) {
                entriesToRemove.add(screenName)
                return@forEach
            }
            
            // Retry if activity is still valid
            if (!activity.isFinishing && !activity.isDestroyed) {
                Log.d("MobileSDK", "🔄 Retrying pending navigation: $screenName")
                triggerByNavigation(screenName, activity)
                entriesToRemove.add(screenName)
            }
        }
        
        // Clean up processed entries
        entriesToRemove.forEach { pendingNavigationTriggers.remove(it) }
    }

    fun triggerByTabChange(tabName: String, activity: Activity) {
        if (isInSurveyCooldown()) return
        
        // CHECK if config is loaded
        if (!configurationLoaded) {
            Log.d("MobileSDK", "⏳ Config not loaded, delaying tab change trigger for: $tabName")
            
            activity.window.decorView.postDelayed({
                if (!activity.isFinishing && !activity.isDestroyed) {
                    Log.d("MobileSDK", "🔄 Retrying tab change trigger for: $tabName")
                    triggerByTabChange(tabName, activity)
                }
            }, 1000)
            return
        }
        
        val normalizedName = tabName.lowercase().trim()
        currentContextName = normalizedName
        Log.d("MobileSDK", "📍 Context updated to: $normalizedName (Tab)")

        val currentTime = System.currentTimeMillis()
        
        // 🛡️ THE FIX: Removed the name check. Block EVERYTHING for 1 second.
        // This safely absorbs the double-hit when the interceptor catches both "tab_womens" and "womens" at the exact same time.
        if (currentTime - lastTriggeredTabTime < 1000) {
            Log.d("MobileSDK", "⚡ Debouncing rapid tab trigger: $normalizedName")
            return
        }

        lastTriggeredTabName = normalizedName
        lastTriggeredTabTime = currentTime
        Log.d("MobileSDK", "📍 triggerByTabChange processing: $normalizedName")

        val matchingSurveys = config.surveys.filter { survey ->
            val isEnabled = survey.enableTabChangeTrigger
            
            // 🛡️ STRICT EQUALITY MATCH: No more .contains bugs. 
            // "mens" will no longer falsely match "womens".
            val matchesTab = survey.triggerTabs.any { configTab ->
                val normalizedConfig = configTab.lowercase().trim()
                // Match exactly "womens" OR "tab_womens"
                normalizedName == normalizedConfig || normalizedName == "tab_$normalizedConfig"
            }
            
            val alreadyTriggered = survey.triggerOnce &&
                    triggeredTabs[survey.surveyId]?.contains(normalizedName) == true
            val isValid = canShowSurvey(survey)

            isEnabled && matchesTab && !alreadyTriggered && isValid
        }

        if (matchingSurveys.isNotEmpty()) {
            val bestSurvey = findHighestPrioritySurvey(matchingSurveys)
            Log.d("MobileSDK", "🎯 TAB MATCHED! Showing survey: ${bestSurvey.surveyId}")
            triggeredTabs[bestSurvey.surveyId]?.add(normalizedName)
            showSingleSurvey(activity, bestSurvey)
        } else {
            Log.d("MobileSDK", "❌ No matching or valid Tab Survey found for '$normalizedName'")
        }
    }

    // ====================================================================
    // PUBLIC API - NAVIGATION INTEGRATION
    // ====================================================================
    fun setupNavigationComponent(navController: NavController, activity: Activity) {
        Log.d("MobileSDK", "=== NAVIGATION SETUP ===")
        Log.d("MobileSDK", "🔄 Activity: ${activity.javaClass.simpleName}")
        var previousScreen: String? = null
        var previousScreenLabel: String? = null

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val screenLabel = destination.label?.toString() ?: "no_label"
            val screenId = destination.id
            val currentScreen = "screen_$screenId"

            Log.d("MobileSDK", "📍 Navigation: $previousScreenLabel → $screenLabel")
            Log.d("MobileSDK", "🎯 Checking for exit trigger between $previousScreenLabel and $screenLabel")

            trackScreenView(activity)

            if (previousScreen != null && previousScreenLabel != null) {
                Log.d("MobileSDK", "🔍 Calling handleScreenExit() from Navigation Component")
                handleScreenExit(previousScreen!!, previousScreenLabel!!, currentScreen, activity)
            }

            screenTimers[currentScreen] = System.currentTimeMillis()
            triggerByNavigation(currentScreen, activity)

            previousScreen = currentScreen
            previousScreenLabel = screenLabel
        }
    }

    // ====================================================================
    // PUBLIC API - SESSION TRACKING
    // ====================================================================
    fun trackAppStart(activity: Activity) {
        ExclusionRuleEvaluator.trackSessionStart(activity)
        Log.d("MobileSDK", "App start tracked")
    }

    fun trackScreenView(activity: Activity) {
        ExclusionRuleEvaluator.trackScreenView(activity)
        
        // 🛡️ FIX 1: Stop Android's "MainActivity" from overwriting React Native's "home"
        if (isHybridMode) return 
        
        val rawName = activity.javaClass.simpleName
        trackScreenView(rawName, activity)
        Log.d("MobileSDK", "Screen view tracked for: $rawName")
    }

    fun trackScreenView(screenName: String, activity: Activity) {
        val normalizedName = screenName.lowercase().trim()
        
        // 🛡️ FIX 2: ALWAYS set the context immediately, even if config is still downloading!
        // This ensures buttons/tabs know what screen they are on right from app launch.
        currentContextName = normalizedName
        Log.d("MobileSDK", "📍 Context updated to: $currentContextName")

        if (!configurationLoaded) {
            Log.d("MobileSDK", "⏳ Config not loaded yet. Context saved, retrying transition evaluation in 1s.")
            activity.window.decorView.postDelayed({
                if (!activity.isFinishing && !activity.isDestroyed) {
                    trackScreenView(screenName, activity)
                }
            }, 1000)
            return
        }

        val isCooldown = isInSurveyCooldown()
        if (isCooldown) return

        if (previousScreen == normalizedName) return
        
        Log.d("MobileSDK", "🔄 Transition: $previousScreen -> $normalizedName")
        handleScreenTransition(previousScreen, normalizedName, activity)
        previousScreen = normalizedName
    }

    fun reSetupNavigationDetection(activity: Activity) {
        Log.d("MobileSDK", "🔄 Manual navigation detection re-setup requested")
        
        if (!configurationLoaded) {
            Log.w("MobileSDK", "⚠️ Config not loaded yet")
            return
        }
        
        // Clear existing listeners
        navigationListenersSet.clear()
        
        // Re-run auto-detection
        Handler(Looper.getMainLooper()).post {
            autoDetectNavigation(activity)
            autoDetectNavigationComponent(activity)
            setupTabChangeTrigger(activity)
            
            Log.d("MobileSDK", "✅ Navigation detection re-setup completed")
        }
    }

    fun resetSessionData() {
        ExclusionRuleEvaluator.resetSessionData(context)
        Log.d("MobileSDK", "Session data reset")
    }

    fun setSessionData(key: String, value: String) {
        ExclusionRuleEvaluator.setSessionData(key, value)
    }

    // PUBLIC API - CONFIGURATION MANAGEMENT
        // ====================================================================
    // Inside MobileSDK class (not SurveyApiService)
    interface ConfigurationCallback {
        fun onConfigurationLoaded(success: Boolean)
        fun onError(error: String)
    }

    fun fetchConfiguration(
        callback: ConfigurationCallback? = null
        ) {
        Log.d("MobileSDK", "🔗 ========== CONFIG FETCH STARTED ==========")
        Log.d("MobileSDK", "📊 Current parameters: ${customParams.size} items")
        
        if (customParams.isNotEmpty()) {
            customParams.forEach { (key, value) ->
                Log.d("MobileSDK", "   • $key = $value")
            }
        }
        
        try {
            // Get cached config with current parameters
            val cachedConfig = configCacheManager.getCachedConfig(customParams)
            if (cachedConfig != null) {
                Log.d("MobileSDK", "📦 Using cached configuration for current parameters")
                Log.d("MobileSDK", "   • Surveys in cache: ${cachedConfig.surveys.size}")
                applyConfiguration(cachedConfig, true, callback)
                return
            }

            Log.d("MobileSDK", "🌐 Fetching FRESH configuration from server...")
            Log.d("MobileSDK", "   • API Key: ${apiKey.take(5)}...")
            
            // Call API service with current parameters
            apiService.fetchConfiguration(
                object : SurveyApiService.ConfigCallback {
                    override fun onConfigLoaded(config: Config?) {
                        if (config != null) {
                            try {
                                Log.d("MobileSDK", "✅ Server response received SUCCESSFULLY")
                                Log.d("MobileSDK", "   • SDK Version: ${config.sdkVersion}")
                                Log.d("MobileSDK", "   • Surveys received: ${config.surveys.size}")
                                
                                // Save to cache with current parameters
                                configCacheManager.saveConfig(config, customParams)
                                Log.d("MobileSDK", "💾 Configuration saved to cache")
                                
                                // Apply new configuration
                                applyConfiguration(config, false, callback)
                                Log.d("MobileSDK", "✅ Configuration applied successfully")
                            } catch (e: Exception) {
                                Log.e("MobileSDK", "❌ Error applying config: ${e.message}")
                                Log.e("MobileSDK", "❌ Stack trace:", e)
                                callback?.onError("Failed to apply configuration: ${e.message}")
                                callback?.onConfigurationLoaded(false)
                            }
                        } else {
                            Log.w("MobileSDK", "⚠️ Server returned NULL config, using fallback")
                            loadFallbackConfig()
                            callback?.onConfigurationLoaded(false)
                        }
                    }

                    override fun onError(error: String) {
                        Log.e("MobileSDK", "❌ Config fetch ERROR: $error")
                        Log.d("MobileSDK", "🔄 Loading fallback configuration...")
                        loadFallbackConfig()
                        callback?.onError(error)
                        callback?.onConfigurationLoaded(false)
                    }
                },
                params = customParams
            )
        } catch (e: Exception) {
            Log.e("MobileSDK", "❌ UNEXPECTED error in fetchConfiguration: ${e.message}")
            Log.e("MobileSDK", "❌ Stack trace:", e)
            loadFallbackConfig()
            callback?.onError("Unexpected error: ${e.message}")
            callback?.onConfigurationLoaded(false)
        }
        
        Log.d("MobileSDK", "🔗 ========== CONFIG FETCH COMPLETED ==========")
    }

    private fun loadConfigurationAutomatically() {
        Log.d("MobileSDK", "📡 ========== AUTO-LOADING CONFIGURATION ==========")
        Log.d("MobileSDK", "   • API Key: ${apiKey.take(5)}...")
        Log.d("MobileSDK", "   • Parameters: ${customParams.size} items")
        
        fetchConfiguration(
            callback = object : ConfigurationCallback {
                override fun onConfigurationLoaded(success: Boolean) {
                    configurationLoaded = true
                    Log.d("MobileSDK", if (success) 
                        "✅ Config auto-loaded SUCCESSFULLY" 
                    else 
                        "⚠️ Config auto-load FAILED - using fallback"
                    )
                }

                override fun onError(error: String) {
                    configurationLoaded = true
                    Log.w("MobileSDK", "⚠️ Auto-config failed: $error - using fallback")
                }
            }
        )
    }

    fun isConfigurationLoaded(): Boolean = configurationLoaded

    // ====================================================================
    // PUBLIC API - DEBUG & STATUS
    // ====================================================================
    fun isUserExcluded(surveyId: String? = null): Boolean {
        if (!this::config.isInitialized) return false

        if (surveyId != null) {
            val survey = config.surveys.find { it.surveyId == surveyId }
            if (survey != null) {
                return ExclusionRuleEvaluator.shouldExcludeSurvey(lastActivity ?: context, survey.exclusionRules)
            }
            return false
        }
        return false
    }

    fun debugSurveyStatus(): String {
        val builder = StringBuilder()
        builder.append("=== MULTI-SURVEY STATUS ===\n")
        builder.append("📋 ${getQueueStatus()}\n")
        builder.append("📱 Currently showing survey: $isShowingSurvey\n\n")

        config.surveys.forEach { survey ->
            val shownCount = surveyShownCount[survey.surveyId] ?: 0
            val lastTime = lastSurveyTime[survey.surveyId] ?: 0
            val timeSinceLastSurvey = System.currentTimeMillis() - lastTime
            val cooldownRemaining = survey.cooldownPeriod - timeSinceLastSurvey
            val canShow = canShowSurvey(survey)

            builder.append(
                """
        📊 Survey: ${survey.surveyId}
        🎯 Priority: ${survey.priority}
        📈 Shown count: $shownCount/${survey.maxShowsPerSession}
        ⏰ Last shown: ${timeSinceLastSurvey / 1000}s ago
        ❄️ Cooldown: ${cooldownRemaining / 1000}s remaining
        🎲 Probability: ${survey.probability}
        ✅ Can show: $canShow
        ---
        """.trimIndent()
            )
        }

        return builder.toString()
    }

    fun debugButtonTriggers(activity: Activity) {
        Log.d("MobileSDK", "=== BUTTON TRIGGER DEBUG ===")

        val buttonTriggerSurveys = config.surveys.filter { it.enableButtonTrigger }
        Log.d("MobileSDK", "Found ${buttonTriggerSurveys.size} button trigger surveys")

        buttonTriggerSurveys.forEach { survey ->
            Log.d("MobileSDK", "📱 Survey: ${survey.surveyId}")
            Log.d("MobileSDK", "   • buttonTriggerId: ${survey.buttonTriggerId}")
            Log.d("MobileSDK", "   • canShowSurvey: ${canShowSurvey(survey)}")

            if (!survey.buttonTriggerId.isNullOrEmpty()) {
                try {
                    val resourceId = activity.resources.getIdentifier(
                        survey.buttonTriggerId,
                        "id",
                        activity.packageName
                    )
                    if (resourceId != 0) {
                        val button = activity.findViewById<View>(resourceId)
                        Log.d("MobileSDK", "   • Button found: ${button != null}")
                        if (button != null) {
                            Log.d("MobileSDK", "   • Button class: ${button::class.java.simpleName}")
                            Log.d("MobileSDK", "   • Button clickable: ${button.isClickable}")
                        }
                    } else {
                        Log.d("MobileSDK", "   • Button ID not found in resources")
                    }
                } catch (e: Exception) {
                    Log.d("MobileSDK", "   • Error finding button: ${e.message}")
                }
            }
        }
    }

    fun debugExitTriggers(activity: Activity) {
        Log.d("MobileSDK", "=== DEBUG EXIT TRIGGERS ===")

        val exitSurveys = config.surveys.filter { it.enableExitTrigger }
        Log.d("MobileSDK", "Found ${exitSurveys.size} exit trigger surveys")

        exitSurveys.forEach { survey : SurveyConfig ->
            Log.d("MobileSDK", "📱 Survey: ${survey.surveyId}")
            Log.d("MobileSDK", "   • triggerScreens: ${survey.triggerScreens}")
            Log.d("MobileSDK", "   • canShow: ${canShowSurvey(survey)}")
            Log.d("MobileSDK", "   • triggerOnce: ${survey.triggerOnce}")

            val triggeredScreensForSurvey = triggeredExits[survey.surveyId] ?: emptySet()
            Log.d("MobileSDK", "   • Already triggered for screens: $triggeredScreensForSurvey")

            if (!canShowSurvey(survey)) {
                debugWhyCannotShow(survey)
            }
        }
    }

    fun getBaseUrlForDebug(): String {
        return if (this::config.isInitialized) {
            "Multi-survey config with ${config.surveys.size} surveys"
        } else {
            "Config not initialized"
        }
    }

    fun getConfigForDebug(): String {
        return if (this::config.isInitialized) {
            "Multi-survey config: ${config.surveys.size} surveys"
        } else {
            "Config not initialized"
        }
    }

    fun isSDKEnabled(): Boolean {
        return configurationLoaded && config.surveys.isNotEmpty() && config.surveys.any { it.status }
    }

    fun getSetupStatus(): String {
        return buildString {
            append("MobileSDK Status:\n")
            append("- Initialized: $initialized\n")
            append("- Config loaded: $configurationLoaded\n")
            append("- Surveys: ${config.surveys.size}\n")
            append("- API Key: ${apiKey.take(5)}...\n")
            append("- Queue: ${getQueueStatus()}")
        }
    }

    fun isUsingServerConfig(): Boolean {
        return if (configurationLoaded && this::config.isInitialized) {
            config.surveys.isNotEmpty() && 
            config.surveys.any { survey -> 
                // Server surveys will have proper URLs, fallback surveys are empty
                survey.baseUrl.isNotEmpty() && !survey.baseUrl.contains("default", ignoreCase = true)
            }
        } else {
            false
        }
    }

    fun debugConfigStatus(): String {
        val builder = StringBuilder()
        builder.append("=== CONFIG DEBUG STATUS ===\n")
        builder.append("• API Key: ${apiKey.take(5)}...\n")
        builder.append("• Parameters: ${customParams.size} items\n")
        builder.append("• Config loaded: $configurationLoaded\n")
        builder.append("• Config source: ${if (isUsingServerConfig()) "SERVER" else "FALLBACK"}\n")
        
        if (this::config.isInitialized) {
            builder.append("• SDK Version: ${config.sdkVersion}\n")
            builder.append("• Surveys in config: ${config.surveys.size}\n")
            
            if (config.surveys.isEmpty()) {
                builder.append("• ⚠️ NO SURVEYS CONFIGURED\n")
                builder.append("• Waiting for server response...\n")
            } else {
                config.surveys.forEachIndexed { index, survey ->
                    builder.append("  ${index + 1}. ${survey.surveyId}\n")
                    builder.append("     • URL: ${survey.baseUrl.take(50)}${if (survey.baseUrl.length > 50) "..." else ""}\n")
                    builder.append("     • Status: ${survey.status}\n")
                    builder.append("     • Source: ${if (survey.baseUrl.isEmpty()) "FALLBACK" else "SERVER"}\n")
                }
            }
        } else {
            builder.append("• Config NOT initialized\n")
        }
        
        return builder.toString()
    }

    fun isReady(): Boolean {
        return initialized && configurationLoaded && config.surveys.isNotEmpty()
    }

    // ====================================================================
    // PUBLIC API - QUEUE MANAGEMENT
    // ====================================================================
    fun getQueueStatus(): String {
        synchronized(queueLock) {
            return if (surveyQueue.isEmpty()) {
                "Queue: Empty"
            } else {
                "Queue: ${surveyQueue.size} surveys - " +
                        surveyQueue.joinToString { "(P:${it.second.priority})" }
            }
        }
    }

    fun clearSurveyQueue() {
        synchronized(queueLock) {
            surveyQueue.clear()
            Log.d("MobileSDK", "🧹 Survey queue cleared")
        }
    }

    fun clearQueueForActivity(activity: Activity) {
        synchronized(queueLock) {
            val initialSize = surveyQueue.size
            surveyQueue.removeAll { it.first == activity }
            val removedCount = initialSize - surveyQueue.size
            if (removedCount > 0) {
                Log.d("MobileSDK", "🧹 Removed $removedCount surveys from queue for destroyed activity")
            }
        }
    }

    fun isShowingSurvey(): Boolean {
        synchronized(queueLock) {
            return isShowingSurvey
        }
    }

    fun getSurveyIds(): List<String> {
        return if (this::config.isInitialized) {
            config.surveys.map { it.surveyId }
        } else {
            emptyList()
        }
    }

    fun resetTriggers() {
        surveyShownCount.clear()
        lastSurveyTime.clear()
        triggeredScreens.clear()
        triggeredTabs.clear()
        triggeredExits.clear()
        screenTimers.clear()
        SafeDelayExecutor.cancelAll()
        delayExecutors.clear()
        navigationListenersSet.clear()
        lastNavigationTriggerTime = 0
    }

    fun cleanup() {
        Log.d("MobileSDK", "🧹 Cleaning up SDK instance...")
        
        // Clear all internal state
        surveyShownCount.clear()
        lastSurveyTime.clear()
        triggeredScreens.clear()
        triggeredTabs.clear()
        triggeredExits.clear()
        screenTimers.clear()
        
        // Cancel all pending operations
        SafeDelayExecutor.cancelAll()
        delayExecutors.clear()
        
        // Clear configuration
        configurationLoaded = false
        
        // Clear API service
        apiService.cleanup()
        
        // Clear other states
        cleanupExitTrigger()
        strongViewReferences.clear()
        processedViews.clear()
        navigationListenersSet.clear()
        lastNavigationTriggerTime = 0

        synchronized(queueLock) {
            surveyQueue.clear()
            isShowingSurvey = false
        }

        Log.d("MobileSDK", "🧹 SDK fully cleaned up")
    }


    private fun resetSurveyTracking() {
        Log.d("MobileSDK", "🔄 Resetting survey tracking...")
        Log.d("MobileSDK_DEBUG", "🔄 Resetting survey tracking. Current counts: $surveyShownCount")

        surveyShownCount.clear()
        lastSurveyTime.clear()
        triggeredScreens.clear()
        triggeredTabs.clear()
        triggeredExits.clear()
        screenTimers.clear()
        
        // Clear all pending triggers
        SafeDelayExecutor.cancelAll()
        delayExecutors.clear()
        
        // Clear navigation tracking
        navigationListenersSet.clear()
        lastNavigationTriggerTime = 0
        
        // Clear survey queue
        synchronized(queueLock) {
            surveyQueue.clear()
            isShowingSurvey = false
        }
        
        // Clear processed views
        processedViews.clear()
        strongViewReferences.clear()

        appLaunchTriggerSetup = false
        
        // Reset other states
        lastSurveyClosedTime = 0
        lastScrollTriggerTime = 0
        lastTriggeredTabName = null
        lastTriggeredTabTime = 0
        lastPauseTime = 0
        lastStopTime = 0
        activeActivities.clear()
        currentContextName = null
        previousScreen = null
        
        Log.d("MobileSDK", "✅ Survey tracking reset")
    }

    // ====================================================================
    // PRIVATE METHODS - SURVEY DISPLAY
    // ====================================================================
    private fun showSurveyInternal(activity: Activity, specificSurvey: SurveyConfig? = null) {
        Log.d("MobileSDK", "🔄 showSurveyInternal called - specificSurvey: ${specificSurvey?.surveyId}")

        if (!configurationLoaded) {
            Log.w("MobileSDK", "Configuration not ready, using fallback")
            loadFallbackConfig()
        }

        try {
            if (specificSurvey != null) {
                Log.d("MobileSDK", "🎯 Showing specific survey: ${specificSurvey.surveyId}")
                if (canShowSurvey(specificSurvey)) {
                    showSingleSurvey(activity, specificSurvey)
                } else {
                    Log.d("MobileSDK", "❌ Cannot show specific survey: ${specificSurvey.surveyId}")
                }
            } else {
                Log.d("MobileSDK", "🎲 Showing any matching survey")
                val matchingSurveys = findMatchingSurveys(activity)
                Log.d("MobileSDK", "📊 Found ${matchingSurveys.size} matching surveys")
                if (matchingSurveys.isNotEmpty()) {
                    val bestSurvey = findHighestPrioritySurvey(matchingSurveys)
                    Log.d("MobileSDK", "🏆 Selected survey: ${bestSurvey.surveyId} (priority: ${bestSurvey.priority})")
                    showSingleSurvey(activity, bestSurvey)
                } else {
                    Log.d("MobileSDK", "❌ No matching surveys found")
                }
            }
        } catch (e: Exception) {
            Log.e("MobileSDK", "❌ Error showing survey: ${e.message}")
        }
    }

    private fun showSingleSurvey(activity: Activity, survey: SurveyConfig) {
        Log.d("MobileSDK", "🎯 [BEFORE] Showing survey ${survey.surveyId}, processedViews size: ${processedViews.size}")


        // Add timeout check
        if (activity.isFinishing || activity.isDestroyed) {
            Log.e("MobileSDK", "❌ Activity invalid, cannot show survey")
            surveyCompleted()
            return
        }
        
        // Add UI thread check
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.e("MobileSDK", "❌ Not on main thread, posting to main thread")
            Handler(Looper.getMainLooper()).post {
                showSingleSurvey(activity, survey)
            }
            return
        }
        


        if (survey == null) {
            Log.e("MobileSDK", "❌ Survey is null")
            surveyCompleted()
            return
        }
        
        val baseUrl = survey.baseUrl ?: ""
        if (baseUrl.isNullOrEmpty()) {
            Log.e("MobileSDK", "❌ Base URL empty for survey")
            surveyCompleted()
            return
        }

        if (activity.isFinishing || activity.isDestroyed) {
            Log.e("MobileSDK", "❌ Cannot show survey - activity is finishing or destroyed")
            surveyCompleted()
            return
        }

        val surveyId = survey.surveyId ?: "unknown"


        synchronized(queueLock) {
            if (isShowingSurvey) {
                val isAlreadyQueued = surveyQueue.any { it.second.surveyId == survey.surveyId }

                if (isAlreadyQueued) {
                    Log.d("MobileSDK", "🚫 Skipping duplicate survey request in queue: $surveyId")
                    return
                }

                surveyQueue.add(activity to survey)
                surveyQueue.sortByDescending { it.second.priority }

                Log.d("MobileSDK", "📋 Survey queued: ${survey.surveyId} (Priority: ${survey.priority})")
                Log.d("MobileSDK", "📋 Queue size: ${surveyQueue.size}")

                Handler(Looper.getMainLooper()).postDelayed({
                    synchronized(queueLock) {
                        if (isShowingSurvey) {
                            Log.w("MobileSDK", "⚠️ Auto-unlocking queue after timeout")
                            isShowingSurvey = false
                            processNextInQueue()
                        }
                    }
                }, 10000)

                return
            }
            isShowingSurvey = true
        }

        try {
            val url = buildSurveyUrl(survey)
            val allowedDomain = getSurveyDomain(survey.baseUrl)

            Log.d("MobileSDK", "🎯 Showing survey: (${survey.surveyId})")
            Log.d("MobileSDK", "🎯 Priority: ${survey.priority}, Queue: ${surveyQueue.size} waiting")

            when (survey.modalStyle) {
                "dialog" -> {
                    Log.d("MobileSDK", "🚀 Launching SurveyDialogFragment")
                    showDialogSurvey(activity, url, allowedDomain, survey.animationType)
                }
                "bottom_sheet" -> {
                    Log.d("MobileSDK", "🚀 Launching SurveyBottomSheetFragment")
                    showBottomSheetSurvey(activity, url, allowedDomain, survey.animationType, survey.backgroundColor)
                }
                else -> {
                    Log.d("MobileSDK", "🚀 Launching SurveyFullScreenActivity")
                    showFullScreenSurvey(activity, url, allowedDomain, survey.animationType, survey.backgroundColor)
                }
            }

            recordSurveyShown(survey.surveyId)

        } catch (e: Exception) {
            Log.e("MobileSDK", "❌ Error showing survey ${survey.surveyId}: ${e.message}")
            surveyCompleted()
        }
    }

    private fun processNextInQueue() {
        synchronized(queueLock) {
            if (surveyQueue.isEmpty()) {
                Log.d("MobileSDK", "📭 Queue processed - no more surveys")
                isShowingSurvey = false
                return
            }

            try {
                val item = surveyQueue.removeAt(0)

                if (item == null) {
                    Log.e("MobileSDK", "❌ Null item found in queue, trying next")
                    processNextInQueue()
                    return
                }

                val (activity, nextSurvey) = item

                if (activity == null) {
                    Log.e("MobileSDK", "❌ Null activity in queue item, trying next")
                    processNextInQueue()
                    return
                }

                if (nextSurvey == null) {
                    Log.e("MobileSDK", "❌ Null survey in queue item, trying next")
                    processNextInQueue()
                    return
                }

                if (activity.isFinishing || activity.isDestroyed) {
                    Log.w("MobileSDK", "⚠️ Activity no longer valid: ${activity.javaClass.simpleName}")
                    processNextInQueue()
                    return
                }

                Log.d("MobileSDK", "🔄 Processing next in queue: ${nextSurvey.surveyId}")
                Log.d("MobileSDK", "📋 Queue remaining: ${surveyQueue.size}")

                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            showSingleSurvey(activity, nextSurvey)
                        } else {
                            Log.w("MobileSDK", "⚠️ Activity became invalid during delay, trying next")
                            processNextInQueue()
                        }
                    } catch (e: Exception) {
                        Log.e("MobileSDK", "❌ Error showing queued survey: ${e.message}")
                        processNextInQueue()
                    }
                }, 500)

            } catch (e: Exception) {
                Log.e("MobileSDK", "❌ Critical error in processNextInQueue: ${e.message}")
                surveyQueue.clear()
                isShowingSurvey = false
            }
        }
    }

    private var surveyCompletionCallback: (() -> Unit)? = null

    fun setSurveyCompletionCallback(callback: () -> Unit) {
        surveyCompletionCallback = callback
    }

    fun notifySurveyCompleted() {
        surveyCompletionCallback?.invoke()
        surveyCompletionCallback = null
    }

    fun surveyCompleted() {
        synchronized(queueLock) {
            isShowingSurvey = false
            lastSurveyClosedTime = System.currentTimeMillis()
            Log.d("MobileSDK", "✅ Survey completed, queue unlocked. Cooldown started.")
        }

        Log.d("MobileSDK", "🎯 [AFTER] Survey completed, processedViews size: ${processedViews.size}")
        processNextInQueue()
        notifySurveyCompleted()
    }

    // ====================================================================
    // PRIVATE METHODS - SURVEY LOGIC
    // ====================================================================
    private fun canShowSurvey(survey: SurveyConfig): Boolean {
        if (survey == null) {
            Log.d(SDKConstants.LOG_TAG_SDK, "❌ Survey is null")
            return false
        }

        if (!this::config.isInitialized) return false

        if (!survey.status) {
            Log.d(SDKConstants.LOG_TAG_SDK, "❌ Survey ${survey.surveyId} is disabled (status: false)")
            return false
        }

        val baseUrl = survey.baseUrl ?: ""
        if (baseUrl.isEmpty()) {
            Log.d(SDKConstants.LOG_TAG_SDK, "❌ Survey ${survey.surveyId} has empty baseUrl")
            return false
        }

        if (ExclusionRuleEvaluator.shouldExcludeSurvey(lastActivity ?: context, survey.exclusionRules)) {
            Log.d(SDKConstants.LOG_TAG_SDK, "❌ Survey ${survey.surveyId} excluded by rules")
            return false
        }

        // "Trigger once": a survey that was already shown to this user is never shown again,
        // for every trigger type and across app restarts.
        if (survey.triggerOnce && hasShownOnce(survey)) {
            Log.d(SDKConstants.LOG_TAG_SDK, "❌ Survey ${survey.surveyId} already shown once (triggerOnce)")
            return false
        }

        // Note: `probability` is intentionally not applied. The field is kept for backward
        // compatibility with the backend payload only.

        val shownCount = surveyShownCount[survey.surveyId] ?: 0
        Log.d("MobileSDK_DEBUG", "🔢 Survey ${survey.surveyId} shownCount: $shownCount, maxShows: ${survey.maxShowsPerSession}")
        if (survey.maxShowsPerSession > 0 && shownCount >= survey.maxShowsPerSession) {
            Log.d(SDKConstants.LOG_TAG_SDK, "❌ Survey ${survey.surveyId} reached max shows: $shownCount/${survey.maxShowsPerSession}")
            Log.d("MobileSDK_DEBUG", "❌ BLOCKED by max shows: $shownCount >= ${survey.maxShowsPerSession}")
            return false
        }

        val lastTime = lastSurveyTime[survey.surveyId] ?: 0
        Log.d(SDKConstants.LOG_TAG_SDK, "✅ Survey Last Time ${lastTime}")
        if (survey.cooldownPeriod > 0) {
            val timeSinceLast = System.currentTimeMillis() - lastTime
            if (timeSinceLast < survey.cooldownPeriod) {
                Log.d(SDKConstants.LOG_TAG_SDK, "❌ Survey ${survey.surveyId} in cooldown: ${timeSinceLast}ms/${survey.cooldownPeriod}ms")
                return false
            }
        }

        Log.d(SDKConstants.LOG_TAG_SDK, "✅ Survey ${survey.surveyId} CAN be shown")
        return true
    }

     private fun recordSurveyShown(surveyId: String) {
        val safeSurveyId = surveyId ?: return
        val currentCount = surveyShownCount[safeSurveyId] ?: 0
        val newCount = currentCount + 1
        surveyShownCount[safeSurveyId] = newCount
        lastSurveyTime[safeSurveyId] = System.currentTimeMillis()
        Log.d("MobileSDK_DEBUG", "📊 Survey shown: $safeSurveyId, count: $currentCount → $newCount")

        if (this::config.isInitialized) {
            config.surveys.find { it.surveyId == safeSurveyId }
                ?.takeIf { it.triggerOnce }
                ?.let { markShownOnce(it) }
        }
    }

    // ===== PERSISTENT "TRIGGER ONCE" MEMORY =====
    // Remembered per survey and per user identity (the parameters given to initialize), so a
    // survey with triggerOnce = true is shown once per user. Without user parameters it is
    // once per device. The data is removed when the app data is cleared.
    private val triggerOnceStore: android.content.SharedPreferences by lazy {
        context.getSharedPreferences("survey_sdk_trigger_once", Context.MODE_PRIVATE)
    }

    private fun triggerOnceKey(survey: SurveyConfig): String = "${survey.surveyId}|${customParams.hashCode()}"

    private fun hasShownOnce(survey: SurveyConfig): Boolean {
        if (survey.surveyId.isBlank()) return false
        return try {
            triggerOnceStore.getBoolean(triggerOnceKey(survey), false)
        } catch (e: Exception) {
            false
        }
    }

    private fun markShownOnce(survey: SurveyConfig) {
        if (survey.surveyId.isBlank()) return
        try {
            triggerOnceStore.edit().putBoolean(triggerOnceKey(survey), true).apply()
            Log.d("MobileSDK", "🔒 triggerOnce survey ${survey.surveyId} remembered for this user")
        } catch (e: Exception) {
            Log.w("MobileSDK", "Could not remember triggerOnce survey: ${e.message}")
        }
    }

    /**
     * Forgets which "trigger once" surveys were already shown (all users on this device).
     * Useful for testing, or when a user signs out and should see the surveys again.
     */
    fun clearTriggerOnceHistory() {
        triggerOnceStore.edit().clear().apply()
        Log.d("MobileSDK", "🧹 triggerOnce history cleared")
    }

    // private fun recordSurveyShown(surveyId: String) {
    //     val safeSurveyId = surveyId ?: return
    //     val currentCount = surveyShownCount[safeSurveyId] ?: 0
    //     surveyShownCount[safeSurveyId] = currentCount + 1
    //     lastSurveyTime[safeSurveyId] = System.currentTimeMillis()
    //     Log.d("MobileSDK", "📊 Survey shown: $safeSurveyId, count: ${surveyShownCount[safeSurveyId]}")
    // }

    private fun findMatchingSurveys(activity: Activity): List<SurveyConfig> {
        return config.surveys.filter { survey -> canShowSurvey(survey) }
    }

    private fun findHighestPrioritySurvey(surveys: List<SurveyConfig>): SurveyConfig {
        return surveys.sortedByDescending { it.priority }.first()
    }

    private fun debugSurveyConditions(survey: SurveyConfig) {
        Log.d("MobileSDK", "🔍 DEBUG Survey ${survey.surveyId}:")
        Log.d("MobileSDK", "   • Status: ${survey.status}")
        Log.d("MobileSDK", "   • Base URL empty: ${survey.baseUrl.isEmpty()}")
        Log.d("MobileSDK", "   • Excluded: ${ExclusionRuleEvaluator.shouldExcludeSurvey(context, survey.exclusionRules)}")
        Log.d("MobileSDK", "   • Probability check: ${Math.random() <= survey.probability}")

        val shownCount = surveyShownCount[survey.surveyId] ?: 0
        Log.d("MobileSDK", "   • Shown count: $shownCount/${survey.maxShowsPerSession}")

        val lastTime = lastSurveyTime[survey.surveyId] ?: 0
        val timeSinceLast = System.currentTimeMillis() - lastTime
        Log.d("MobileSDK", "   • Time since last: ${timeSinceLast}ms (cooldown: ${survey.cooldownPeriod}ms)")
    }

    private fun debugWhyCannotShow(survey: SurveyConfig) {
        Log.d("MobileSDK", "   ❓ Why cannot show ${survey.surveyId}:")
        Log.d("MobileSDK", "     • status=${survey.status}")
        Log.d("MobileSDK", "     • baseUrl empty=${survey.baseUrl.isEmpty()}")
        Log.d("MobileSDK", "     • excluded=${ExclusionRuleEvaluator.shouldExcludeSurvey(context, survey.exclusionRules)}")
        Log.d("MobileSDK", "     • probability=${survey.probability}")

        val shownCount = surveyShownCount[survey.surveyId] ?: 0
        Log.d("MobileSDK", "     • shownCount=$shownCount/${survey.maxShowsPerSession}")

        val lastTime = lastSurveyTime[survey.surveyId] ?: 0
        val timeSinceLast = System.currentTimeMillis() - lastTime
        Log.d("MobileSDK", "     • timeSinceLast=${timeSinceLast}ms, cooldown=${survey.cooldownPeriod}ms")
    }

    // ====================================================================
    // PRIVATE METHODS - URL & DATA BUILDING
    // ====================================================================
    private fun buildSurveyUrl(survey: SurveyConfig): String {
        Log.d("MobileSDK", "🔗 === BUILD SURVEY URL ===")
        
        return try {
            val params = collectAllData(survey)
            val queryString = params.entries.joinToString("&") { "${it.key}=${it.value}" }
            val finalUrl = "${survey.baseUrl}&$queryString"
            
            Log.d("MobileSDK", "📝 Base URL: ${survey.baseUrl}")
            Log.d("MobileSDK", "📝 Query params: $queryString")
            Log.d("MobileSDK", "🔗 FINAL URL: $finalUrl")
            Log.d("MobileSDK", "🔗 === URL BUILD COMPLETE ===")
            
            finalUrl
        } catch (e: Exception) {
            Log.e("MobileSDK", "❌ Error building survey URL: ${e.message}")
            survey.baseUrl
        }
    }

    private fun collectAllData(survey: SurveyConfig): Map<String, String> {
        Log.d("MobileSDK", "🎯 === COLLECT ALL DATA START ===")
        Log.d("MobileSDK", "Survey: ${survey.surveyId}")
        
        val data = mutableMapOf<String, String>()

        try {
            // 1. ESSENTIAL: Always include surveyId
            //data["surveyId"] = survey.surveyId
            Log.d("MobileSDK", "✅ Added surveyId: ${survey.surveyId}")

            // 2. ONLY collect custom parameters from configuration
            val customParams = collectCustomParams(survey)
            data.putAll(customParams)
            Log.d("MobileSDK", "📥 Added ${customParams.size} custom params")
            
            // 3. OPTIONAL: Only collect if EXPLICITLY enabled
            if (survey.collectDeviceId || survey.collectDeviceModel) {
                val deviceInfo = DeviceUtils.getDeviceInfo(context)
                data.putAll(deviceInfo)
                Log.d("MobileSDK", "📱 Added ${deviceInfo.size} device params")
            }
            
            if (survey.collectLocation) {
                val locationData = DeviceUtils.getLocationData(context)
                data.putAll(locationData)
                Log.d("MobileSDK", "📍 Added ${locationData.size} location params")
            }
            
            if (survey.collectAppUsage) {
                val appUsageData = AppUsageUtils.getAppUsageData(context)
                data.putAll(appUsageData)
                Log.d("MobileSDK", "📊 Added ${appUsageData.size} app usage params")
            }

            Log.d("MobileSDK", "📤 FINAL: Collected ${data.size} parameters total")
            data.forEach { (key, value) ->
                Log.d("MobileSDK", "   • $key = $value")
            }

        } catch (e: Exception) {
            Log.e("MobileSDK", "❌ Error collecting survey data: ${e.message}")
            // Fallback
            data["surveyId"] = survey.surveyId
        }
        
        Log.d("MobileSDK", "🎯 === COLLECT ALL DATA END ===")
        return data
    }

    private fun collectCustomParams(survey: SurveyConfig): Map<String, String> {
        val params = mutableMapOf<String, String>()
        
        Log.d("MobileSDK", "=== COLLECT CUSTOM PARAMS ===")
        Log.d("MobileSDK", "Survey: ${survey.surveyId}")
        Log.d("MobileSDK", "Custom params config: ${survey.customParams.size} items")
        
        survey.customParams.forEachIndexed { index, param ->
            Log.d("MobileSDK", "  [$index] ${param.name} (source: ${param.source}, key: ${param.key})")
        }
        
        survey.customParams.forEach { param ->
            try {
                Log.d("MobileSDK", "🔍 Processing param: ${param.name}")
                
                val paramValue = when (param.source) {
                    ParamSource.STORAGE -> {
                        val value = StorageUtils.findSpecificData(context, param.key ?: param.name)
                        Log.d("MobileSDK", "   📦 STORAGE lookup for '${param.key ?: param.name}': $value")
                        value
                    }
                    ParamSource.DEVICE -> {
                        // Device info plus battery, charging, network flags, country and screen size.
                        // "unknown" (e.g. location without permission) counts as "no value".
                        ExclusionRuleEvaluator.getDeviceValue(context, param.key ?: param.name)
                            ?.takeUnless { it == "unknown" }
                            .also {
                                Log.d("MobileSDK", "   📱 DEVICE lookup for '${param.key ?: param.name}': $it")
                            }
                    }
                    ParamSource.TIMESTAMP -> {
                        // millis (default), seconds, iso, hour, day_of_week, day_of_month, month
                        ExclusionRuleEvaluator.getTimestampValue(param.key ?: "millis").also {
                            Log.d("MobileSDK", "   ⏰ TIMESTAMP '${param.key ?: "millis"}': $it")
                        }
                    }
                    ParamSource.SESSION -> {
                        val sessionKey = param.key ?: param.name
                        ExclusionRuleEvaluator.getSessionValue(context, sessionKey).also {
                            Log.d("MobileSDK", "   🧭 SESSION lookup for '$sessionKey': $it")
                        }
                    }
                    ParamSource.INSTALL_TIME -> {
                        ExclusionRuleEvaluator.getInstallTimeValue(context, param.key ?: "days").also {
                            Log.d("MobileSDK", "   📅 INSTALL_TIME '${param.key ?: "days"}': $it")
                        }
                    }
                    ParamSource.APP_USAGE -> {
                        ExclusionRuleEvaluator.getAppUsageValue(context, param.key ?: "sessions").also {
                            Log.d("MobileSDK", "   📈 APP_USAGE '${param.key ?: "sessions"}': $it")
                        }
                    }
                    ParamSource.URL -> {
                        // Reads a query parameter / extra of the current Activity's Intent
                        ExclusionRuleEvaluator.getUrlParameter(lastActivity ?: context, param.key ?: param.name).also {
                            Log.d("MobileSDK", "   🔗 URL lookup for '${param.key ?: param.name}': $it")
                        }
                    }
                }
                
                // Add the value when the source produced one; otherwise skip the parameter
                if (paramValue != null && paramValue.isNotEmpty()) {
                    params[param.name] = paramValue
                    Log.d("MobileSDK", "   ✅ ADDED: ${param.name} = $paramValue")
                } else {
                    Log.d("MobileSDK", "   ⚠️ SKIPPED: ${param.name} (no value)")
                }
                
            } catch (e: Exception) {
                Log.e("MobileSDK", "❌ Error collecting param ${param.name}: ${e.message}")
            }
        }
        
        Log.d("MobileSDK", "📋 Total custom params collected: ${params.size}")
        Log.d("MobileSDK", "Params: $params")
        Log.d("MobileSDK", "=== END COLLECT CUSTOM PARAMS ===")
        
        return params
    }

    // Helper methods (add these to your class)
    private fun getSessionData(key: String): String? {
        // Implement based on your session storage
        return null
    }

    private fun getInstallTimeValue(unit: String): String? {
        // Implement install time retrieval
        return null
    }

    private fun getUrlParameter(key: String): String? {
        // Implement URL parameter retrieval
        return null
    }

    private fun getSurveyDomain(baseUrl: String): String? {
        return try {
            java.net.URI(baseUrl).host
        } catch (e: Exception) {
            null
        }
    }

    // ====================================================================
    // PRIVATE METHODS - CONFIGURATION MANAGEMENT
    // ====================================================================
    private fun applyConfiguration(
        config: Config,
        fromCache: Boolean,
        callback: ConfigurationCallback?
        ) {
        // Clear existing tracking
        resetSurveyTracking()
        
        this.config = config
        configurationLoaded = true
        
        Log.d("MobileSDK", "🎯 ========== CONFIGURATION APPLIED ==========")
        Log.d("MobileSDK", "   • Source: ${if (fromCache) "CACHE" else "NETWORK"}")
        Log.d("MobileSDK", "   • Surveys: ${config.surveys.size}")
        Log.d("MobileSDK", "   • SDK Version: ${config.sdkVersion}")
        
        // Initialize tracking for new surveys
        config.surveys.forEach { survey ->
            surveyShownCount[survey.surveyId] = 0
            lastSurveyTime[survey.surveyId] = 0
            triggeredScreens[survey.surveyId] = mutableSetOf()
            triggeredTabs[survey.surveyId] = mutableSetOf()
            triggeredExits[survey.surveyId] = mutableSetOf()
        }
        
        Log.d("MobileSDK", "✅ Tracking initialized for ${config.surveys.size} surveys")

        config.surveys.forEachIndexed { index, survey ->
            Log.d("TRACE", "   📋 Survey ${index + 1}: ${survey.surveyId}")
            Log.d("TRACE", "   • Has customParams: ${survey.customParams.isNotEmpty()}")
            Log.d("TRACE", "   • customParams count: ${survey.customParams.size}")
            
            survey.customParams.forEach { param ->
                Log.d("TRACE", "     - ${param.name} (source: ${param.source}, key: ${param.key})")
            }
        }
        
        // **CRITICAL FIX: Re-run ALL trigger setups if we have an active activity**
        lastActivity?.let { activity ->
            if (!activity.isFinishing && !activity.isDestroyed) {
                Log.d("MobileSDK", "🔄 Re-running ALL trigger setups with new config")
                
                Handler(Looper.getMainLooper()).postDelayed({
                    // Clear previous setups
                    scrollViewsWithListeners.clear()
                    processedViews.clear()
                    strongViewReferences.clear()
                    
                    // Re-run ALL setups
                    autoDetectNavigation(activity)
                    autoDetectNavigationComponent(activity)
                    setupTabChangeTrigger(activity)
                    setupAutoScreenTracking(activity)
                    setupSmartButtonDetection(activity)
                    setupScrollTrigger(activity)  // ADD THIS LINE
                    setupScrollViewObserver(activity)  // ADD THIS LINE
                    setupAppLaunchTrigger(activity)
                    
                    Log.d("MobileSDK", "✅ All trigger setups re-run with new config")
                }, 300)
            }
        }
        
        val configValid = config.surveys.isNotEmpty()
        callback?.onConfigurationLoaded(configValid)
        Log.d("MobileSDK", "🎯 ========== CONFIGURATION APPLICATION COMPLETE ==========")
    }

    fun debugBackendResponse(): String {
        return try {
            val builder = StringBuilder()
            builder.append("=== BACKEND RESPONSE DEBUG ===\n")
            builder.append("• API Key: ${apiKey.take(5)}...\n")
            builder.append("• Config loaded: $configurationLoaded\n")
            builder.append("• Has config: ${this::config.isInitialized}\n")
            
            if (this::config.isInitialized) {
                builder.append("• SDK Version: ${config.sdkVersion}\n")
                builder.append("• Cache hours: ${config.cacheDurationHours}\n")
                builder.append("• Surveys: ${config.surveys.size}\n")
                
                if (config.surveys.isEmpty()) {
                    builder.append("• ⚠️ NO SURVEYS - Check:\n")
                    builder.append("   1. Backend database\n")
                    builder.append("   2. API key permissions\n")
                    builder.append("   3. Survey configuration\n")
                }
            }
            
            builder.toString()
        } catch (e: Exception) {
            "Error debugging: ${e.message}"
        }
    }

    fun testBackendConnection(callback: (success: Boolean, message: String) -> Unit) {
        Log.d("MobileSDK", "🧪 ========== TESTING BACKEND CONNECTION ==========")
        
        fetchConfiguration(object : ConfigurationCallback {
            override fun onConfigurationLoaded(success: Boolean) {
                if (success && config.surveys.isNotEmpty()) {
                    callback(true, "✅ Backend connection successful! Received ${config.surveys.size} surveys.")
                } else if (success && config.surveys.isEmpty()) {
                    callback(false, "⚠️ Backend connected but returned empty config.")
                } else {
                    callback(false, "❌ Failed to load configuration from backend.")
                }
            }
            
            override fun onError(error: String) {
                callback(false, "❌ Backend error: $error")
            }
        })
    }

    private fun loadFallbackConfig() {
        // Clear existing tracking first
        resetSurveyTracking()
        
        // Load fallback config
        config = DefaultConfig.getFallbackConfig()
        configurationLoaded = true
        
        // Initialize tracking for fallback surveys
        config.surveys.forEach { survey ->
            surveyShownCount[survey.surveyId] = 0
            lastSurveyTime[survey.surveyId] = 0
            triggeredScreens[survey.surveyId] = mutableSetOf()
            triggeredTabs[survey.surveyId] = mutableSetOf()
            triggeredExits[survey.surveyId] = mutableSetOf()
        }
        
        Log.d("MobileSDK", "📋 Fallback configuration loaded with ${config.surveys.size} surveys")
    }

    fun debugCurrentConfig(): String {
        return buildString {
            append("=== CURRENT CONFIG STATUS ===\n")
            append("• API Key: ${apiKey.take(5)}...\n")
            append("• Parameters: ${customParams.size} items\n")
            append("• Config loaded: $configurationLoaded\n")
            append("• Surveys in config: ${if (this@MobileSDK::config.isInitialized) config.surveys.size else 0}\n")
            
            if (this@MobileSDK::config.isInitialized) {
                config.surveys.forEachIndexed { index, survey ->
                    append("  ${index + 1}. ${survey.surveyId}\n")
                }
            }
        }
    }

    // ====================================================================
    // PRIVATE METHODS - TRIGGER SETUP
    // ====================================================================
    private fun setupSmartButtonDetection(activity: Activity, retryCount: Int = 0) {
        Log.d("MobileSDK", "🎯 Smart Button Detection STARTING (retry: $retryCount)")
        
        val surveysWithButtonTrigger = config.surveys.filter { it.enableButtonTrigger }
        if (surveysWithButtonTrigger.isEmpty()) {
            Log.d("MobileSDK", "📭 No button trigger surveys in config")
            return
        }

        Log.d("MobileSDK", "🔍 Found ${surveysWithButtonTrigger.size} button trigger surveys")

        val customButtonSurveys = surveysWithButtonTrigger
            .filter { !it.buttonTriggerId.isNullOrEmpty() }
            .groupBy { it.buttonTriggerId!! }

        val predefinedSurveys = surveysWithButtonTrigger
            .filter { it.buttonTriggerId.isNullOrEmpty() }

        Log.d("MobileSDK", "📊 Custom buttons: ${customButtonSurveys.size}, Predefined: ${predefinedSurveys.size}")

        var buttonsFound = 0
        
        customButtonSurveys.forEach { (buttonId, surveys) ->
            if (setupCustomButtonTrigger(activity, buttonId, surveys)) {
                buttonsFound++
            }
        }

        if (predefinedSurveys.isNotEmpty()) {
            val highestPrioritySurvey = predefinedSurveys.maxByOrNull { it.priority }
            highestPrioritySurvey?.let { survey ->
                val found = setupPredefinedButtonTriggers(activity, survey)
                buttonsFound += found  // FIXED: Just add the Int value
            }
        }
        
        Log.d("MobileSDK", "✅ Button detection completed: $buttonsFound buttons found")
        
        // If no buttons found, retry once after delay
        if (buttonsFound == 0 && retryCount == 0) {
            Log.d("MobileSDK", "🔄 No buttons found, will retry after UI settles")
            activity.window.decorView.postDelayed({
                setupSmartButtonDetection(activity, retryCount + 1)
            }, 1500)
        }
    }

    private fun setupCustomButtonTrigger(activity: Activity, buttonId: String, surveys: List<SurveyConfig>): Boolean {
        try {
            Log.d("MobileSDK", "🔍 Looking for custom button: $buttonId")

            val resourceId = activity.resources.getIdentifier(buttonId, "id", activity.packageName)
            if (resourceId == 0) {
                Log.w("MobileSDK", "⚠️ Custom button ID not found in resources: $buttonId")
                
                // Try to find by tag or content description
                return findButtonByAlternativeMethods(activity, buttonId, surveys.first())
            }
            
            val button = activity.findViewById<View>(resourceId)
            if (button != null) {
                val survey = surveys.first()
                Log.d("MobileSDK", "✅ Found custom button: $buttonId → ${survey.surveyId}")
                setupSingleButtonTrigger(button, activity, survey)
                return true
            } else {
                Log.w("MobileSDK", "⚠️ Custom button found by ID but View is null: $buttonId")
                
                // Try deferred search
                activity.window.decorView.post {
                    val buttonDeferred = activity.findViewById<View>(resourceId)
                    if (buttonDeferred != null) {
                        val survey = surveys.first()
                        Log.d("MobileSDK", "✅ Found custom button (deferred): $buttonId")
                        setupSingleButtonTrigger(buttonDeferred, activity, survey)
                    }
                }
                return false
            }
        } catch (e: Exception) {
            Log.e("MobileSDK", "❌ Error setting up custom button $buttonId: ${e.message}")
            return false
        }
    }

    private fun findButtonByAlternativeMethods(activity: Activity, identifier: String, survey: SurveyConfig): Boolean {
        // Helper function to recursively find view by tag
        fun findViewWithTag(view: View, tag: String): View? {
            if (view.tag?.toString() == tag) {
                return view
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    val found = findViewWithTag(view.getChildAt(i), tag)
                    if (found != null) return found
                }
            }
            return null
        }
        
        // Helper function to recursively find view by content description
        fun findViewWithContentDescription(view: View, description: String): View? {
            if (view.contentDescription?.toString() == description) {
                return view
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    val found = findViewWithContentDescription(view.getChildAt(i), description)
                    if (found != null) return found
                }
            }
            return null
        }
        
        // Try to find button by tag
        val rootView = activity.window.decorView
        val foundView = findViewWithTag(rootView, identifier)
        
        if (foundView != null) {
            Log.d("MobileSDK", "✅ Found button by tag: $identifier")
            setupSingleButtonTrigger(foundView, activity, survey)
            return true
        }
        
        // Try to find by content description
        val foundByDesc = findViewWithContentDescription(rootView, identifier)
        if (foundByDesc != null) {
            Log.d("MobileSDK", "✅ Found button by content description: $identifier")
            setupSingleButtonTrigger(foundByDesc, activity, survey)
            return true
        }
        
        return false
    }

    // Add this to your PUBLIC API section
    fun retryTriggerSetup(activity: Activity) {
        Log.d("MobileSDK", "🔄 Manual trigger setup retry requested")
        
        if (!configurationLoaded) {
            Log.w("MobileSDK", "⚠️ Configuration not loaded yet")
            return
        }
        
        Handler(Looper.getMainLooper()).post {
            // Clear existing processed views to force re-scan
            processedViews.clear()
            strongViewReferences.clear()
            scrollViewsWithListeners.clear()
            
            // Re-run setup methods
            startGlobalViewScanning(activity)
            setupSmartButtonDetection(activity)
            setupScrollTrigger(activity)
            
            Log.d("MobileSDK", "✅ Manual trigger setup retry completed")
        }
    }

    private fun setupPredefinedButtonTriggers(activity: Activity, survey: SurveyConfig): Int {
        Log.d("MobileSDK", "🔍 Setting up predefined buttons for survey: ${survey.surveyId}")
        
        var buttonsFound = 0
        val predefinedIds = SDKConstants.PREDEFINED_BUTTON_IDS

        predefinedIds.forEach { buttonId ->
            try {
                val resourceId = activity.resources.getIdentifier(buttonId, "id", activity.packageName)
                if (resourceId != 0) {
                    val button = activity.findViewById<View>(resourceId)
                    if (button != null) {
                        Log.d("MobileSDK", "✅ Found predefined button: $buttonId")
                        setupSingleButtonTrigger(button, activity, survey)
                        buttonsFound++
                    }
                }
            } catch (e: Exception) {
                Log.e("MobileSDK", "❌ Error setting up predefined button $buttonId: ${e.message}")
            }
        }
        
        Log.d("MobileSDK", "📊 Setup $buttonsFound predefined buttons")
        return buttonsFound
    }

    private fun setupSingleButtonTrigger(button: View, activity: Activity, survey: SurveyConfig) {
        var lastClickTime = 0L
        val debounceTime = SDKConstants.BUTTON_DEBOUNCE_MS

        button.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < debounceTime) return@setOnClickListener
            lastClickTime = currentTime

            Log.d("MobileSDK", "🎯 Button clicked → Survey: ${survey.surveyId} (Priority: ${survey.priority})")

            button.isEnabled = false
            button.alpha = 0.5f

            if (canShowSurvey(survey)) {
                Log.d("MobileSDK", "🚀 Triggering survey: ${survey.surveyId}")
                showSingleSurvey(activity, survey)
            } else {
                Log.d("MobileSDK", "❌ Cannot show survey ${survey.surveyId} - conditions not met")
            }

            Handler(Looper.getMainLooper()).postDelayed({
                button.isEnabled = true
                button.alpha = 1.0f
            }, SDKConstants.SURVEY_QUEUE_DELAY_MS)
        }
    }

    private fun setupScrollViewObserver(activity: Activity) {
        // CHECK: Wait for config to load
        if (!configurationLoaded) {
            Log.d("MobileSDK", "⏳ Config not loaded, delaying scroll view observer by 1s...")
            activity.window.decorView.postDelayed({
                setupScrollViewObserver(activity)
            }, 1000)
            return
        }
        
        // CHECK: Ensure config has scroll trigger surveys
        val needsScrollSetup = config.surveys.any {
            it.enableScrollTrigger && it.scrollThreshold > 0
        }
        
        if (!needsScrollSetup) {
            Log.d("MobileSDK", "📭 No scroll trigger surveys in config")
            return
        }
        
        val rootView = activity.window.decorView
        
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            try {
                val scrollableViews = mutableListOf<View>()
                findMainScrollView(rootView, scrollableViews)
                
                if (scrollableViews.isNotEmpty()) {
                    Log.d("MobileSDK", "🔄 Found scroll views, setting up listeners")
                    setupScrollTrigger(activity)
                }
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }

    private fun setupScrollTrigger(activity: Activity) {
    // CHECK: Wait for config to load
        if (!configurationLoaded) {
            Log.d("MobileSDK", "⏳ Config not loaded, delaying scroll trigger by 1s...")
            activity.window.decorView.postDelayed({
                setupScrollTrigger(activity)
            }, 1000)
            return
        }
        
        // CHECK: Ensure config has surveys
        if (config.surveys.isEmpty()) {
            Log.d("MobileSDK", "📭 No surveys in config, skipping scroll trigger")
            return
        }

        Log.d("MobileSDK", "=== SETUP SCROLL TRIGGER ===")

        val surveysWithScrollTrigger = config.surveys.filter {
            it.enableScrollTrigger && it.scrollThreshold > 0
        }

        Log.d("MobileSDK", "Found ${surveysWithScrollTrigger.size} scroll trigger surveys")
        surveysWithScrollTrigger.forEach { survey ->
            Log.d("MobileSDK", "• ${survey.surveyId}: threshold=${survey.scrollThreshold}")
        }

        if (surveysWithScrollTrigger.isEmpty()) {
            Log.d("MobileSDK", "❌ No scroll trigger surveys found")
            return
        }

        activity.window.decorView.postDelayed({
            val scrollableViews = mutableListOf<View>()
            findMainScrollView(activity.window.decorView, scrollableViews)

            if (scrollableViews.isEmpty()) {
                findScrollViewByIds(activity, scrollableViews)
            }

            if (scrollableViews.isEmpty()) {
                val contentView = activity.findViewById<ViewGroup>(android.R.id.content)
                if (contentView != null) {
                    findMainScrollView(contentView, scrollableViews)
                }
            }

            Log.d("MobileSDK", "🎯 Found ${scrollableViews.size} scrollable views")

            if (scrollableViews.isEmpty()) {
                Log.d("MobileSDK", "❌ No scrollable views found in the activity")
                debugViewHierarchy(activity.window.decorView)
            } else {
                scrollableViews.forEach { scrollView ->
                    val viewHash = scrollView.hashCode()

                    if (!scrollViewsWithListeners.contains(viewHash)) {
                        Log.d("MobileSDK", "• ${scrollView::class.java.simpleName} @ ${System.identityHashCode(scrollView)} - NEW")
                        setupScrollViewListener(scrollView, activity, surveysWithScrollTrigger)
                        scrollViewsWithListeners.add(viewHash)
                    } else {
                        Log.d("MobileSDK", "• ${scrollView::class.java.simpleName} @ ${System.identityHashCode(scrollView)} - SKIP (already has listener)")
                    }
                }
            }
        }, 1000)
    }

    private fun findMainScrollView(view: View, result: MutableList<View>) {
        when (view) {
            is ScrollView -> {
                Log.d("MobileSDK", "✅ Found ScrollView: ${view.id}")
                result.add(view)
                return
            }
            is NestedScrollView -> {
                Log.d("MobileSDK", "✅ Found NestedScrollView: ${view.id}")
                result.add(view)
                return
            }
            is androidx.recyclerview.widget.RecyclerView -> {
                Log.d("MobileSDK", "✅ Found RecyclerView: ${view.id}")
                result.add(view)
                return
            }
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findMainScrollView(view.getChildAt(i), result)
                if (result.isNotEmpty()) return
            }
        }
    }

    private fun findScrollViewByIds(activity: Activity, result: MutableList<View>) {
        val commonScrollViewIds = listOf(
            "scroll_view",
            "nested_scroll_view",
            "recycler_view",
            "list_view",
            "scroll_container"
        )

        commonScrollViewIds.forEach { idName ->
            try {
                val resId = activity.resources.getIdentifier(idName, "id", activity.packageName)
                if (resId != 0) {
                    val view = activity.findViewById<View>(resId)
                    if (view != null && (view is ScrollView || view is NestedScrollView || view is androidx.recyclerview.widget.RecyclerView)) {
                        Log.d("MobileSDK", "✅ Found scroll view by ID: $idName")
                        result.add(view)
                    }
                }
            } catch (e: Exception) {
                // Continue
            }
        }
    }

    private fun debugViewHierarchy(view: View, depth: Int = 0) {
        val indent = "  ".repeat(depth)
        Log.d("MobileSDK", "$indent${view::class.java.simpleName} [id=${view.id}]")

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                debugViewHierarchy(view.getChildAt(i), depth + 1)
            }
        }
    }

    private fun setupScrollViewListener(scrollView: View, activity: Activity, surveys: List<SurveyConfig>) {
        var triggered = false

        when (scrollView) {
            is ScrollView -> {
                scrollView.viewTreeObserver.addOnScrollChangedListener {
                    if (!triggered) {
                        val scrollY = scrollView.scrollY
                        Log.d("MobileSDK", "📜 ScrollView scrollY: $scrollY")
                        checkScrollThreshold(scrollY, activity, surveys)?.let {
                            triggered = true
                        }
                    }
                }
            }
            is NestedScrollView -> {
                scrollView.viewTreeObserver.addOnScrollChangedListener {
                    if (!triggered) {
                        val scrollY = scrollView.scrollY
                        Log.d("MobileSDK", "📜 NestedScrollView scrollY: $scrollY")
                        checkScrollThreshold(scrollY, activity, surveys)?.let {
                            triggered = true
                        }
                    }
                }
            }
            is androidx.recyclerview.widget.RecyclerView -> {
                scrollView.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                        super.onScrolled(recyclerView, dx, dy)
                        if (!triggered) {
                            val scrollY = recyclerView.computeVerticalScrollOffset()
                            Log.d("MobileSDK", "📜 RecyclerView scrollY: $scrollY, dy: $dy")
                            checkScrollThreshold(scrollY, activity, surveys)?.let {
                                triggered = true
                            }
                        }
                    }
                })
            }
        }

        Log.d("MobileSDK", "✅ Scroll listener setup on ${scrollView::class.java.simpleName}")
    }

    private fun checkScrollThreshold(scrollY: Int, activity: Activity, surveys: List<SurveyConfig>): SurveyConfig? {
        val activeContext = currentContextName
            ?: previousScreen
            ?: activity.javaClass.simpleName.lowercase()

        Log.d("MobileSDK", "🔍 [SCROLL DEBUG] Checking threshold: scrollY=$scrollY, context='$activeContext'")
        Log.d("MobileSDK", "🔍 [SCROLL DEBUG] Surveys to check: ${surveys.size}")
        
        surveys.forEach { survey ->
            Log.d("MobileSDK", "🔍 [SCROLL DEBUG] Survey ${survey.surveyId}:")
            Log.d("MobileSDK", "  • threshold=${survey.scrollThreshold}")
            Log.d("MobileSDK", "  • meetsThreshold=${scrollY >= survey.scrollThreshold}")
            Log.d("MobileSDK", "  • triggerScreens=${survey.triggerScreens}")
            Log.d("MobileSDK", "  • matchesScreen=${survey.triggerScreens.isEmpty() || survey.triggerScreens.any { activeContext.contains(it, ignoreCase = true) }}")
            Log.d("MobileSDK", "  • canShow=${canShowSurvey(survey)}")
        }

        val matchingSurveys = surveys.filter { survey ->
            val meetsThreshold = scrollY >= survey.scrollThreshold
            val matchesScreen = survey.triggerScreens.isEmpty() ||
                    survey.triggerScreens.any {
                        activeContext.contains(it, ignoreCase = true)
                    }
            val canShow = canShowSurvey(survey)

            val matches = survey.enableScrollTrigger && meetsThreshold && matchesScreen && canShow
            
            Log.d("MobileSDK", "🔍 [SCROLL DEBUG] Survey ${survey.surveyId} matches: $matches")
            Log.d("MobileSDK", "  • enableScrollTrigger=${survey.enableScrollTrigger}")
            Log.d("MobileSDK", "  • meetsThreshold=$meetsThreshold ($scrollY >= ${survey.scrollThreshold})")
            Log.d("MobileSDK", "  • matchesScreen=$matchesScreen")
            Log.d("MobileSDK", "  • canShow=$canShow")
            
            matches
        }

        Log.d("MobileSDK", "🔍 [SCROLL DEBUG] Matching surveys found: ${matchingSurveys.size}")

        if (matchingSurveys.isNotEmpty()) {
            val bestSurvey = findHighestPrioritySurvey(matchingSurveys)
            Log.d("MobileSDK", "🎯 [SCROLL] Scroll trigger FIRED! Showing: ${bestSurvey.surveyId}")
            showSingleSurvey(activity, bestSurvey)
            return bestSurvey
        } else {
            Log.d("MobileSDK", "❌ [SCROLL] No matching surveys found")
        }

        return null
    }

    private fun debugTraceSetupAppLaunchTrigger() {
        val stackTrace = Thread.currentThread().stackTrace
        Log.d("MobileSDK", "🔍 setupAppLaunchTrigger() CALLED FROM:")
        Log.d("MobileSDK", "   1. ${stackTrace[3].className}.${stackTrace[3].methodName}():${stackTrace[3].lineNumber}")
        Log.d("MobileSDK", "   2. ${stackTrace[4].className}.${stackTrace[4].methodName}():${stackTrace[4].lineNumber}")
        Log.d("MobileSDK", "   3. ${stackTrace[5].className}.${stackTrace[5].methodName}():${stackTrace[5].lineNumber}")
        
        Log.d("MobileSDK", "🔍 === END TRACE ===")
    }

    private fun setupAppLaunchTrigger(activity: Activity) {

        debugTraceSetupAppLaunchTrigger()

        Log.d("MobileSDK", "🚀 === SETUP APP LAUNCH TRIGGER ===")
        Log.d("MobileSDK", "   • Caller: ${Throwable().stackTrace[2].methodName}")
        Log.d("MobileSDK", "   • Thread: ${Thread.currentThread().name}")
        Log.d("MobileSDK", "   • Activity: ${activity::class.simpleName}")
        Log.d("MobileSDK", "   • Config loaded: $configurationLoaded")

        // === ADD THESE 3 LINES ===
        if (appLaunchTriggerSetup) {
            Log.d("MobileSDK", "🛑 STOP: App launch trigger already setup!")
            return
        }
        appLaunchTriggerSetup = true
        // =========================

        // CHECK 1: Wait for config to load
        if (!configurationLoaded) {
            Log.d("MobileSDK", "⏳ Config not loaded, delaying app launch trigger by 1s...")
            activity.window.decorView.postDelayed({
                setupAppLaunchTrigger(activity)
            }, 1000)
            return
        }
        
        val surveysWithAppLaunch = config.surveys.filter { it.enableAppLaunchTrigger }
        
        if (surveysWithAppLaunch.isEmpty()) {
            Log.d("MobileSDK", "📭 No app launch surveys in config")
            return
        }
        
        surveysWithAppLaunch.forEach { survey ->
            if (survey.triggerType == "delayed" && survey.timeDelay > 0) {
                val executor = ActivitySafeExecutor(activity)
                delayExecutors["app_launch_${survey.surveyId}"] = executor
                executor.executeDelayed(survey.timeDelay) { safeActivity ->
                    safeActivity?.let { if (canShowSurvey(survey)) showSingleSurvey(it, survey) }
                }
            } else if (canShowSurvey(survey)) {
                showSingleSurvey(activity, survey)
            }
        }
    }

    private fun setupTabChangeTrigger(activity: Activity) {
         // CHECK 1: Wait for config to load
        if (!configurationLoaded) {
            Log.d("MobileSDK", "⏳ Config not loaded, delaying tab change trigger by 1s...")
            activity.window.decorView.postDelayed({
                setupTabChangeTrigger(activity)
            }, 1000)
            return
        }
        
        // CHECK 2: Ensure config has tab change surveys
        val tabSurveys = config.surveys.filter { it.enableTabChangeTrigger }
        if (tabSurveys.isEmpty()) {
            Log.d("MobileSDK", "📭 No tab change surveys in config")
            return
        }
        
        Log.d("MobileSDK", "📍 Setting up tab change trigger for ${tabSurveys.size} surveys")
    }

    private fun setupActivityLifecycle(activity: Activity) {
        activityLifecycleCallbacks = createActivityLifecycleCallbacks()
        activity.application.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
    }

    private fun setupScreenTimeTrigger(
        screenName: String,
        activity: Activity,
        survey: SurveyConfig
    ) {
        if (survey.triggerType != "delayed" || survey.timeDelay <= 0) return

        val executorKey = "screen_${screenName}_${survey.surveyId}"
        val executor = ActivitySafeExecutor(activity)
        delayExecutors[executorKey] = executor

        executor.executeDelayed(survey.timeDelay) { safeActivity ->
            val screenEntryTime = screenTimers[screenName]
            safeActivity?.let {
                if (screenEntryTime != null && canShowSurvey(survey)) {
                    triggeredScreens[survey.surveyId]?.add(screenName)
                    showSingleSurvey(it, survey)
                }
            }
        }
    }

    private fun setupTabTimeTrigger(tabName: String, activity: Activity, survey: SurveyConfig) {
        if (survey.triggerType != "delayed" || survey.timeDelay <= 0) return

        val executorKey = "tab_${tabName}_${survey.surveyId}"
        val executor = ActivitySafeExecutor(activity)
        delayExecutors[executorKey] = executor

        executor.executeDelayed(survey.timeDelay) { safeActivity ->
            val tabEntryTime = screenTimers[tabName]
            safeActivity?.let {
                if (tabEntryTime != null && canShowSurvey(survey)) {
                    triggeredTabs[survey.surveyId]?.add(tabName)
                    showSingleSurvey(it, survey)
                }
            }
        }
    }

    // ====================================================================
    // PRIVATE METHODS - SCREEN TRACKING & TRANSITIONS
    // ====================================================================
    private fun trackScreenChangeForExit(newScreen: String, activity: Activity) {
        Log.d("MobileSDK", "🔄 Checking exit triggers for screen change")

        val oldScreen = previousScreen
        previousScreen = newScreen

        if (oldScreen != null && oldScreen != newScreen) {
            Log.d("MobileSDK", "📱 Screen changed: $oldScreen → $newScreen")

            val exitSurveys = config.surveys.filter { survey ->
                survey.enableExitTrigger &&
                        survey.triggerScreens.isNotEmpty() &&
                        survey.triggerScreens.any { oldScreen.contains(it, ignoreCase = true) }
            }

            Log.d("MobileSDK", "🔍 Found ${exitSurveys.size} exit surveys for leaving: $oldScreen")

            exitSurveys.forEach { survey ->
                if (canShowSurvey(survey)) {
                    Log.d("MobileSDK", "🚪 Showing exit survey for leaving: $oldScreen")
                    showSingleSurvey(activity, survey)
                }
            }
        }
    }

    private fun handleScreenTransition(oldScreen: String?, newScreen: String, activity: Activity) {
        val candidates = mutableListOf<SurveyConfig>()
        Log.d("MobileSDK", "   handleScreenTransition")
        
        // A. Find EXIT Candidates (Leaving Old Screen)
        if (oldScreen != null) {
            Log.d("MobileSDK", "   handleScreenTransition oldScreen")
            val exitMatches = config.surveys.filter { survey ->
                survey.enableExitTrigger &&
                        survey.triggerScreens.any { oldScreen.contains(it, ignoreCase = true) } &&
                        canShowSurvey(survey)
            }
            candidates.addAll(exitMatches)
            Log.d("MobileSDK", " exitMatches  Found ${exitMatches.size} Exit surveys for '$oldScreen'")
            if (exitMatches.isNotEmpty()) {
                Log.d("MobileSDK", "   Found ${exitMatches.size} Exit surveys for '$oldScreen'")
            }
        }

        // B. Find NAVIGATION Candidates (Entering New Screen)
        val navMatches = config.surveys.filter { survey ->
            survey.enableNavigationTrigger &&
                    survey.triggerScreens.any { newScreen.contains(it, ignoreCase = true) } &&
                    canShowSurvey(survey)
        }
        candidates.addAll(navMatches)
        Log.d("MobileSDK", " navMatches Found ${navMatches.size} Nav surveys for '$newScreen'")
        if (navMatches.isNotEmpty()) {
            Log.d("MobileSDK", "   Found ${navMatches.size} Nav surveys for '$newScreen'")
        }
        Log.d("MobileSDK", "   handleScreenTransition candidates  ${candidates.isNotEmpty()}")

        // C. Priority Sorting
        if (candidates.isNotEmpty()) {
            val sortedCandidates = candidates.sortedWith(
                compareByDescending<SurveyConfig> { it.priority }
                    .thenByDescending { it.enableExitTrigger }
            )

            Log.d("MobileSDK", "⚡ Queuing ${sortedCandidates.size} surveys in priority order")

            sortedCandidates.forEach { survey ->
                if (survey.enableExitTrigger) triggeredExits[survey.surveyId]?.add(oldScreen ?: "")
                if (survey.enableNavigationTrigger) triggeredScreens[survey.surveyId]?.add(newScreen)
                showSingleSurvey(activity, survey)
            }
        }
    }

    private fun setupAutoScreenTracking(activity: Activity) {
        // CHECK 1: Wait for config to load
        if (!configurationLoaded) {
            Log.d("MobileSDK", "⏳ Config not loaded, delaying screen tracking by 1s...")
            activity.window.decorView.postDelayed({
                setupAutoScreenTracking(activity)
            }, 1000)
            return
        }
        
        // CHECK 2: Ensure config has surveys
        if (config.surveys.isEmpty()) {
            Log.d("MobileSDK", "📭 No surveys in config, skipping auto screen tracking")
            return
        }

        Log.d("MobileSDK", "🔄 Setting up auto screen tracking")

        try {
            autoDetectNavigationComponent(activity)
        } catch (e: Exception) {
            Log.d("MobileSDK", "⚠️ Navigation component auto-detection failed")
        }

        val rootView = activity.window.decorView
        var lastClickedText: String? = null

        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            try {
                findAndTrackScreenText(rootView, activity, lastClickedText)?.let { newText ->
                    lastClickedText = newText
                }
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }

    private fun findAndTrackScreenText(view: View, activity: Activity, lastText: String?): String? {
        if (view is TextView && view.isClickable && !view.text.isNullOrEmpty()) {
            val currentText = view.text.toString().trim()
            if (currentText.length > 25) return null

            if (currentText == lastText) {
                return null
            }

            if (isInSurveyCooldown()) {
                Log.d("MobileSDK", "🛡️ Auto-Tracking blocked by cooldown: $currentText")
                return currentText
            }

            Log.d("MobileSDK", "👀 Detected NEW text: $currentText (Old: $lastText)")

            val isTab = config.surveys.any { survey ->
                survey.enableTabChangeTrigger &&
                        survey.triggerTabs.any { it.equals(currentText, ignoreCase = true) }
            }

            val isNav = config.surveys.any { survey ->
                survey.enableNavigationTrigger &&
                        survey.triggerScreens.any { it.equals(currentText, ignoreCase = true) }
            }

            return when {
                isTab -> {
                    Log.d("MobileSDK", "🛡️ Auto-Tracking: '$currentText' is a TAB.")
                    triggerByTabChange(currentText, activity)
                    currentText
                }
                isNav -> {
                    Log.d("MobileSDK", "🔄 Auto-Tracking: '$currentText' is a SCREEN.")
                    trackScreenView(currentText, activity)
                    currentText
                }
                else -> {
                    Log.d("MobileSDK", "💤 Auto-Tracking: '$currentText' ignored (Not in config)")
                    null
                }
            }
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val result = findAndTrackScreenText(view.getChildAt(i), activity, lastText)
                if (result != null) return result
            }
        }
        return null
    }

    private fun trackScreenChange(newScreen: String, activity: Activity) {
        Log.d("MobileSDK", "=== TRACK SCREEN CHANGE ===")
        Log.d("MobileSDK", "Old screen: $previousScreen")
        Log.d("MobileSDK", "New screen: $newScreen")
        val oldScreen = previousScreen
        previousScreen = newScreen

        if (oldScreen != null) {
            Log.d("MobileSDK", "🔄 Screen change: $oldScreen → $newScreen")

            val exitSurveys = config.surveys.filter { survey ->
                survey.enableExitTrigger &&
                        survey.triggerScreens.any { oldScreen.contains(it, ignoreCase = true) }
            }

            exitSurveys.forEach { survey ->
                if (canShowSurvey(survey)) {
                    Log.d("MobileSDK", "🚪 Showing exit survey for leaving: $oldScreen")
                    showSingleSurvey(activity, survey)
                }
            }
        }
    }

    // ====================================================================
    // PRIVATE METHODS - NAVIGATION DETECTION
    // ====================================================================
    private fun autoDetectNavigationComponent(activity: Activity) {
        if (navigationSafetyEnabled) {
            Log.d("MobileSDK", "🛡️ Skipping navigation component detection (safety mode)")
            return
        }
        try {
            val commonNavHostIds = listOf(
                "nav_host_fragment",
                "nav_host_fragment_activity_main",
                "nav_host",
                "fragment_container"
            )

            for (idName in commonNavHostIds) {
                try {
                    val navHostId = activity.resources.getIdentifier(idName, "id", activity.packageName)
                    if (navHostId != 0) {
                        val navHostFragment = (activity as androidx.fragment.app.FragmentActivity)
                            .supportFragmentManager.findFragmentById(navHostId)

                        if (navHostFragment is androidx.navigation.fragment.NavHostFragment) {
                            val navController = navHostFragment.navController
                            Log.d("MobileSDK", "✅ Auto-detected Navigation Component via ID: $idName")
                            setupNavigationComponent(navController, activity)
                            return
                        }
                    }
                } catch (e: Exception) {
                    // Continue trying other IDs
                }
            }

            if (activity is androidx.fragment.app.FragmentActivity) {
                val navHostFragment = activity.supportFragmentManager.fragments.find {
                    it is androidx.navigation.fragment.NavHostFragment
                } as? androidx.navigation.fragment.NavHostFragment

                navHostFragment?.let {
                    val navController = it.navController
                    Log.d("MobileSDK", "✅ Auto-detected Navigation Component in fragments")
                    setupNavigationComponent(navController, activity)
                    return
                }
            }

            Log.d("MobileSDK", "ℹ️ No Navigation Component detected in autoSetup")

        } catch (e: Exception) {
            Log.d("MobileSDK", "⚠️ Navigation Component auto-detection error: ${e.message}")
        }
    }

    // private fun autoDetectNavigation(activity: Activity) {
    //     if (navigationSafetyEnabled) {
    //         Log.d("MobileSDK", "🛡️ Skipping all auto navigation detection (safety mode)")
    //         return
    //     }

    //     if (!configurationLoaded) {
    //         Log.d("MobileSDK", "⏳ Config not loaded, delaying navigation detection...")
    //         activity.window.decorView.postDelayed({
    //             autoDetectNavigation(activity)
    //         }, 1000)
    //         return
    //     }
        
    //     // Check if we have navigation/tab surveys in the NEW config
    //     val hasNavSurveys = config.surveys.any { 
    //         it.enableNavigationTrigger || it.enableTabChangeTrigger 
    //     }
        
    //     if (!hasNavSurveys) {
    //         Log.d("MobileSDK", "📭 No navigation/tab surveys in current config")
    //         return
    //     }
        
    //     try {
    //         Log.d("MobileSDK", "🔄 Starting/Restarting auto navigation detection...")
            
    //         val activityKey = activity.javaClass.simpleName
            
    //         // Clear previous listeners to avoid duplicates
    //         navigationListenersSet.remove("${activityKey}_bottom_nav")
    //         navigationListenersSet.remove("${activityKey}_nav_component")
            
    //         // Method 1: Try BottomNavigationView detection
    //         val bottomNavView = findBottomNavigationView(activity)
    //         if (bottomNavView != null) {
    //             Log.d("MobileSDK", "✅ Found BottomNavigationView - setting up listener")
                
    //             if (navigationSafetyEnabled) {
    //                 Log.d("MobileSDK", "🛡️ Safety mode - using non-interfering observation")
    //                 setupNonInterferingObservation(bottomNavView, activity)
    //             } else {
    //                 setupSimpleBottomNavListener(bottomNavView, activity)
    //             }
                
    //             navigationListenersSet.add("${activityKey}_bottom_nav")
    //             return
    //         }
            
    //         // Method 2: Try Navigation Component
    //         setupNavigationComponentDetection(activity)
            
    //         // Method 3: Try ViewPager
    //         findAndSetupViewPager(activity)
            
    //         Log.d("MobileSDK", "✅ Auto navigation detection setup completed")

    //     } catch (e: Exception) {
    //         Log.e("MobileSDK", "❌ Auto navigation detection failed: ${e.message}")
    //     }
    // }

    private fun autoDetectNavigation(activity: Activity) {
    if (!configurationLoaded) {
        Log.d("MobileSDK", "⏳ Config not loaded, delaying navigation detection...")
        activity.window.decorView.postDelayed({
            autoDetectNavigation(activity)
        }, 1000)
        return
    }
    
    // Check if we have navigation/tab surveys
    val hasNavSurveys = config.surveys.any { 
        it.enableNavigationTrigger || it.enableTabChangeTrigger 
    }
    
    if (!hasNavSurveys) {
        Log.d("MobileSDK", "📭 No navigation/tab surveys in current config")
        return
    }
    
    try {
        Log.d("MobileSDK", "🔄 Starting/Restarting auto navigation detection...")
        
        // Method 1: Try BottomNavigationView detection
        val bottomNavView = findBottomNavigationView(activity)
        if (bottomNavView != null) {
            Log.d("MobileSDK", "✅ Found BottomNavigationView - setting up listener")
            
            if (navigationSafetyEnabled) {
                Log.d("MobileSDK", "🛡️ Safety mode - using passive observation")
                setupNonInterferingObservation(bottomNavView, activity)
            } else {
                setupSimpleBottomNavListener(bottomNavView, activity)
            }
            return
        }
        
        // Method 2: Try Navigation Component
        setupNavigationComponentDetection(activity)
        
        Log.d("MobileSDK", "✅ Auto navigation detection setup completed")

    } catch (e: Exception) {
        Log.e("MobileSDK", "❌ Auto navigation detection failed: ${e.message}")
    }
}

    private fun setupNonInterferingObservation(navView: View, activity: Activity) {
        try {
            if (navView is com.google.android.material.bottomnavigation.BottomNavigationView) {
                val checkSelection = object : Runnable {
                    private var lastSelectedId = -1

                    override fun run() {
                        try {
                            val currentSelectedId = navView.selectedItemId
                            if (currentSelectedId != lastSelectedId && currentSelectedId != -1) {
                                lastSelectedId = currentSelectedId

                                val menu = navView.menu
                                for (i in 0 until menu.size()) {
                                    val item = menu.getItem(i)
                                    if (item.itemId == currentSelectedId) {
                                        val screenName = item.title?.toString()?.toLowerCase() ?: "tab_$currentSelectedId"

                                        Handler(Looper.getMainLooper()).post {
                                            Log.d("MobileSDK", "🛡️ Safety mode - Observed nav change: $screenName")
                                            triggerByNavigation(screenName, activity)
                                        }
                                        break
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Silent fail
                        }

                        navView.postDelayed(this, 500)
                    }
                }

                navView.postDelayed(checkSelection, 1000)
            }
        } catch (e: Exception) {
            // Silent fail
        }
    }

    private fun setupSimpleBottomNavListener(navView: View, activity: Activity) {
        try {
            if (navigationSafetyEnabled) {
                Log.d("MobileSDK", "🛡️ Using passive observation (safety mode)")
                setupNonInterferingObservation(navView, activity) // You already have this method!
                return
            }

            if (navView is com.google.android.material.bottomnavigation.BottomNavigationView) {
                Log.d("MobileSDK", "🔄 Setting up ACTIVE BottomNavigationView listener")
                
                // Store original listener to chain calls
                val originalListener = navView.getOnNavigationItemSelectedListenerField()
                
                navView.setOnNavigationItemSelectedListener { item ->
                    // Call original listener first (if exists)
                    val originalResult = originalListener?.onNavigationItemSelected(item) ?: true
                    
                    // Then handle our logic
                    val rawTitle = item.title?.toString() ?: ""
                    val identifier = rawTitle.lowercase().trim()
                    
                    Log.d("MobileSDK", "📍 BottomNav Clicked: '$rawTitle' (ID: $identifier)")
                    
                    // Check if it's a tab or screen
                    val isTab = config.surveys.any { survey ->
                        survey.enableTabChangeTrigger &&
                                survey.triggerTabs.any { tabName ->
                                    val cleanTab = tabName.lowercase().trim()
                                    identifier == cleanTab || identifier.contains(cleanTab) || cleanTab.contains(identifier)
                                }
                    }
                    
                    Handler(Looper.getMainLooper()).post {
                        if (isTab) {
                            Log.d("MobileSDK", "✅ Identified as TAB: '$identifier'")
                            triggerByTabChange(identifier, activity)
                        } else {
                            Log.d("MobileSDK", "✅ Identified as SCREEN: '$identifier'")
                            triggerByNavigation(identifier, activity)
                        }
                    }
                    
                    originalResult // Return original result to maintain app behavior
                }
                
                Log.d("MobileSDK", "✅ Active BottomNavigationView listener setup complete")
            }
        } catch (e: Exception) {
            Log.e("MobileSDK", "❌ Bottom nav setup failed: ${e.message}")
        }
    }

    // Helper to get original listener via reflection (safe way)
    private fun com.google.android.material.bottomnavigation.BottomNavigationView.getOnNavigationItemSelectedListenerField(): com.google.android.material.bottomnavigation.BottomNavigationView.OnNavigationItemSelectedListener? {
        return try {
            val field = this::class.java.getDeclaredField("listener")
            field.isAccessible = true
            field.get(this) as? com.google.android.material.bottomnavigation.BottomNavigationView.OnNavigationItemSelectedListener
        } catch (e: Exception) {
            null
        }
    }

    private fun setupPassiveNavigationObserver(
        navView: com.google.android.material.bottomnavigation.BottomNavigationView,
        activity: Activity
    ) {
        var lastSelectedId = navView.selectedItemId

        navView.viewTreeObserver.addOnDrawListener {
            try {
                val currentSelectedId = navView.selectedItemId

                if (currentSelectedId != lastSelectedId && currentSelectedId != -1) {
                    lastSelectedId = currentSelectedId

                    val menu = navView.menu
                    for (i in 0 until menu.size()) {
                        val item = menu.getItem(i)
                        if (item.itemId == currentSelectedId) {
                            val rawTitle = item.title?.toString() ?: ""
                            val identifier = rawTitle.lowercase().trim()

                            Log.d("MobileSDK", "👀 Observed BottomNav Click: '$rawTitle' (ID: $identifier)")

                            val isDefinedAsTab = config.surveys.any { survey ->
                                survey.enableTabChangeTrigger &&
                                        survey.triggerTabs.any { tabName ->
                                            val cleanTab = tabName.lowercase().trim()
                                            identifier == cleanTab || identifier.contains(cleanTab) || cleanTab.contains(identifier)
                                        }
                            }

                            Handler(Looper.getMainLooper()).post {
                                if (isDefinedAsTab) {
                                    Log.d("MobileSDK", "✅ Identified as TAB: '$identifier' -> Running Tab Logic")
                                    triggerByTabChange(identifier, activity)
                                } else {
                                    Log.d("MobileSDK", "🔄 Identified as SCREEN: '$identifier' -> Running Screen Logic")
                                    trackScreenView(identifier, activity)
                                }
                            }
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                // Silent fail
            }
        }
        Log.d("MobileSDK", "✅ Passive Navigation Observer Setup Complete")
    }

    private fun setupNavigationComponentDetection(activity: Activity) {
        try {
            if (activity is androidx.fragment.app.FragmentActivity) {
                val activityKey = activity.javaClass.simpleName
                if (navigationListenersSet.contains("${activityKey}_nav_component")) {
                    Log.d("MobileSDK", "⚠️ Navigation component already set up for $activityKey")
                    return
                }

                val navHostFragment = activity.supportFragmentManager.fragments.find {
                    it is androidx.navigation.fragment.NavHostFragment
                } as? androidx.navigation.fragment.NavHostFragment

                navHostFragment?.let { navHost ->
                    val navController = navHost.navController
                    Log.d("MobileSDK", "✅ Found Navigation Component")

                    navController.addOnDestinationChangedListener { _, destination, _ ->
                        val screenName = destination.label?.toString() ?: "screen_${destination.id}"
                        Log.d("MobileSDK", "📍 Navigation Component: $screenName")
                        triggerByNavigation(screenName, activity)
                        triggerByTabChange(screenName, activity)
                    }

                    navigationListenersSet.add("${activityKey}_nav_component")
                }
            }
        } catch (e: Exception) {
            Log.e("MobileSDK", "❌ Navigation component setup failed: ${e.message}")
        }
    }

    private fun findBottomNavigationView(activity: Activity): View? {
        return findViewByClassName(
            activity.window.decorView,
            "com.google.android.material.bottomnavigation.BottomNavigationView"
        )
    }

    private fun findViewByClassName(root: View, className: String): View? {
        if (root::class.java.name == className) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findViewByClassName(root.getChildAt(i), className)
                if (found != null) return found
            }
        }
        return null
    }

    private fun findAndSetupViewPager(activity: Activity) {
        val viewPager =
            findViewByClassName(activity.window.decorView, "androidx.viewpager.widget.ViewPager")
                ?: findViewByClassName(
                    activity.window.decorView,
                    "androidx.viewpager2.widget.ViewPager2"
                )

        if (viewPager != null) {
            setupViewPagerListener(viewPager, activity)
        }
    }

    private fun setupViewPagerListener(viewPager: Any, activity: Activity) {
        try {
            if (viewPager::class.java.name == "androidx.viewpager.widget.ViewPager") {
                val method = viewPager.javaClass.getMethod(
                    "addOnPageChangeListener",
                    Class.forName("androidx.viewpager.widget.ViewPager\$OnPageChangeListener")
                )

                val listener = createViewPagerListener(activity)
                method.invoke(viewPager, listener)
            }
        } catch (e: Exception) {
            Log.e("MobileSDK", "Failed to setup ViewPager listener: ${e.message}")
        }
    }

    private fun createViewPagerListener(activity: Activity): Any {
        return java.lang.reflect.Proxy.newProxyInstance(
            activity.classLoader,
            arrayOf(Class.forName("androidx.viewpager.widget.ViewPager\$OnPageChangeListener"))
        ) { _, method, args ->
            if (method.name == "onPageSelected") {
                val position = args!![0] as Int
                val screenName = "tab_$position"
                triggerByTabChange(screenName, activity)
            }
            false
        }
    }

    // ====================================================================
    // PRIVATE METHODS - VIEW TREE & SCANNING
    // ====================================================================
    private fun setupViewTreeObserver(activity: Activity) {
        val rootView = activity.window.decorView

        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            setupSmartButtonDetection(activity)
        }
    }

    private fun startGlobalViewScanning(activity: Activity) {
        // CHECK 1: Wait for config to load
        if (!configurationLoaded) {
            Log.d("MobileSDK", "⏳ Config not loaded, delaying global view scanning by 1s...")
            activity.window.decorView.postDelayed({
                startGlobalViewScanning(activity)
            }, 1000)
            return
        }
        
        // CHECK 2: Ensure config has surveys
        if (config.surveys.isEmpty()) {
            Log.d("MobileSDK", "📭 No surveys in config, skipping global view scanning")
            return
        }
        
        val rootView = activity.window.decorView.rootView
        
        scanAndAttachListenersSafely(rootView, activity)
        
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            try {
                scanAndAttachListenersSafely(rootView, activity)
            } catch (e: Exception) {
                // Handle potential concurrent modification exceptions
            }
        }
    }

    private fun scanAndAttachListenersSafely(view: View, activity: Activity) {
        val isNavigationComponent = view is com.google.android.material.bottomnavigation.BottomNavigationView ||
                view is androidx.viewpager.widget.ViewPager ||
                view is androidx.viewpager2.widget.ViewPager2 ||
                view::class.java.name.contains("Navigation", ignoreCase = true)

        if (isNavigationComponent) {
            Log.d("MobileSDK", "🚫 Skipping scan for navigation component: ${view::class.java.simpleName}")
            return
        }

        if (processedViews.containsKey(view)) return

        // 1. BUTTON DETECTION
        val tag = view.tag?.toString()
        val contentDesc = view.contentDescription?.toString()
        val viewId = tag ?: contentDesc

        if (viewId != null) {
            val isButtonMatch = config.surveys.any {
                it.enableButtonTrigger && it.buttonTriggerId == viewId
            }

            if (isButtonMatch || SDKConstants.PREDEFINED_BUTTON_IDS.contains(viewId)) {
                if (!isNavigationComponent) {
                    attachPassiveTouchListener(view, viewId, activity, "BUTTON")
                    processedViews[view] = true
                    Log.d("MobileSDK", "✅ Attached listener to button: $viewId")
                } else {
                    Log.d("MobileSDK", "🚫 Skipping navigation button: $viewId")
                }
            }
        }

        // 2. TEXT DETECTION for navigation/tab triggers
        if (view is TextView && !view.text.isNullOrEmpty() && !isNavigationComponent) {
            val textContent = view.text.toString().trim()

            if (textContent.length > 25) return

            val tabMatch = config.surveys.find { survey ->
                survey.enableTabChangeTrigger &&
                        survey.triggerTabs.any { it.equals(textContent, ignoreCase = true) }
            }

            val navMatch = config.surveys.find { survey ->
                survey.enableNavigationTrigger &&
                        survey.triggerScreens.any { it.equals(textContent, ignoreCase = true) }
            }

            if (tabMatch != null || navMatch != null) {
                val clickableParent = findClickableParent(view)

                val parentIsNavigation = clickableParent is com.google.android.material.bottomnavigation.BottomNavigationView ||
                        clickableParent::class.java.name.contains("Navigation", ignoreCase = true)

                if (!parentIsNavigation) {
                    val isInteractive = clickableParent.isClickable ||
                            clickableParent.hasOnClickListeners() ||
                            clickableParent.accessibilityDelegate != null ||
                            (clickableParent is ViewGroup && clickableParent.importantForAccessibility != View.IMPORTANT_FOR_ACCESSIBILITY_NO)

                    if (isInteractive) {
                        val isTab = tabMatch != null
                        val listenerType = if (isTab) "TAB_TEXT" else "NAVIGATION_TEXT"

                        Log.d("MobileSDK", "🧭 Text Match: '$textContent' -> Type: $listenerType")
                        attachPassiveTouchListener(clickableParent, textContent, activity, listenerType)
                        processedViews[view] = true
                        processedViews[clickableParent] = true
                    }
                }
            }
        }

        // 3. Recursively scan child views
        if (view is ViewGroup && !isNavigationComponent) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)

                val childIsNavigation = child is com.google.android.material.bottomnavigation.BottomNavigationView ||
                        child is androidx.viewpager.widget.ViewPager ||
                        child is androidx.viewpager2.widget.ViewPager2 ||
                        child::class.java.name.contains("Navigation", ignoreCase = true)

                if (!childIsNavigation) {
                    scanAndAttachListenersSafely(child, activity)
                } else {
                    Log.d("MobileSDK", "🚫 Skipping child navigation component: ${child::class.java.simpleName}")
                }
            }
        }
    }

    private fun findClickableParent(view: View): View {
        var currentView = view
        var parent = currentView.parent
        var depth = 0

        while (parent is View && depth < 5) {
            val parentView = parent as View

            val isNavigation = parentView is com.google.android.material.bottomnavigation.BottomNavigationView ||
                    parentView is androidx.viewpager.widget.ViewPager ||
                    parentView is androidx.viewpager2.widget.ViewPager2 ||
                    parentView::class.java.name.contains("Navigation", ignoreCase = true)

            if (isNavigation) {
                Log.d("MobileSDK", "⚠️ Found navigation component in parent chain, stopping")
                return currentView
            }

            if (parentView.isClickable || parentView.hasOnClickListeners() || parentView.accessibilityDelegate != null) {
                return parentView
            }

            currentView = parentView
            parent = currentView.parent
            depth++
        }

        return view
    }

    private fun attachPassiveTouchListener(
        view: View,
        identifier: String,
        activity: Activity,
        listenerType: String
    ) {
        val isNavigationComponent = view is com.google.android.material.bottomnavigation.BottomNavigationView ||
                view is androidx.viewpager.widget.ViewPager ||
                view is androidx.viewpager2.widget.ViewPager2 ||
                view::class.java.name.contains("Navigation", ignoreCase = true)

        if (isNavigationComponent) {
            Log.d("MobileSDK", "🚫 CRITICAL: Won't attach listener to navigation component class")
            return
        }

        if (processedViews.containsKey(view)) {
            return
        }

        val originalDelegate = view.accessibilityDelegate

        view.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun sendAccessibilityEvent(host: View, eventType: Int) {
                if (eventType == android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED) {
                    Log.d("MobileSDK", "👆 Detected Click ($listenerType): $identifier")
                    handleDetectedInteraction(identifier, listenerType, activity)
                }
                originalDelegate?.sendAccessibilityEvent(host, eventType) ?: super.sendAccessibilityEvent(host, eventType)
            }
        }

        view.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                Log.d("MobileSDK", "👆 Touch detected ($listenerType): $identifier")
                handleDetectedInteraction(identifier, listenerType, activity)
            }
            false
        }

        strongViewReferences.add(view)
        processedViews[view] = true

        Log.d("MobileSDK", "✅ Attached listener ($listenerType) to: $identifier")
    }

    private fun handleDetectedInteraction(identifier: String, type: String, activity: Activity) {
        Handler(Looper.getMainLooper()).post {
            when (type) {
                "TAB_TEXT" -> {
                    triggerByTabChange(identifier, activity)
                }
                "NAVIGATION_TEXT" -> {
                    trackScreenView(identifier, activity)
                }
                "BUTTON" -> {
                    triggerButtonByStringId(identifier, activity)
                }
                else -> {
                    Log.w("MobileSDK", "⚠️ Unknown interaction type: $type")
                }
            }
        }
    }

    private fun triggerSurvey(survey: SurveyConfig, activity: Activity) {
        Log.d("MobileSDK", "🚀 Attempting to trigger survey: ${survey.surveyId} with style: ${survey.modalStyle}")

        val fragmentManager = (activity as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager

        when (survey.modalStyle) {
            "bottom_sheet" -> {
                if (fragmentManager != null) {
                    try {
                        val bottomSheet = SurveyBottomSheetFragment.newInstance(
                            surveyUrl = survey.baseUrl,
                            backgroundColor = survey.backgroundColor ?: "#FFFFFF",
                            allowedDomain = null
                        )

                        if (fragmentManager.findFragmentByTag("SurveyBottomSheetFragment") == null) {
                            bottomSheet.show(fragmentManager, "SurveyBottomSheetFragment")
                            Log.d("MobileSDK", "✅ Survey opened as BottomSheet")
                        }
                    } catch (e: Exception) {
                        Log.e("MobileSDK", "❌ Failed to open BottomSheet", e)
                    }
                } else {
                    Log.e("MobileSDK", "❌ Cannot open BottomSheet: Activity is not FragmentActivity")
                }
            }

            "full_screen" -> {
                try {
                    val intent = android.content.Intent(activity, SurveyFullScreenActivity::class.java).apply {
                        putExtra("SURVEY_URL", survey.baseUrl)
                        putExtra("BACKGROUND_COLOR", survey.backgroundColor ?: "#FFFFFF")
                        putExtra("ANIMATION_TYPE", survey.animationType ?: "fade")
                        putExtra("SURVEY_ID", survey.surveyId)
                    }
                    activity.startActivity(intent)
                    Log.d("MobileSDK", "✅ Survey opened as FullScreen Activity")
                } catch (e: Exception) {
                    Log.e("MobileSDK", "❌ Failed to open FullScreen Activity", e)
                }
            }

            else -> { // "dialog"
                if (fragmentManager != null) {
                    try {
                        val dialogFragment = SurveyDialogFragment.newInstance(
                            surveyUrl = survey.baseUrl,
                            backgroundColor = survey.backgroundColor ?: "#FFFFFF",
                            animationType = survey.animationType ?: "fade",
                            allowedDomain = null
                        )

                        if (fragmentManager.findFragmentByTag("MobileSDK_Dialog") == null) {
                            dialogFragment.show(fragmentManager, "MobileSDK_Dialog")
                            Log.d("MobileSDK", "✅ Survey opened as Dialog")
                        }
                    } catch (e: Exception) {
                        Log.e("MobileSDK", "❌ Failed to open Dialog", e)
                    }
                } else {
                    Log.e("MobileSDK", "❌ Cannot open Dialog: Activity is not FragmentActivity")
                }
            }
        }
    }

    // ====================================================================
    // PRIVATE METHODS - SCROLL DETECTION
    // ====================================================================
    private fun findScrollableViews(viewGroup: ViewGroup, activity: Activity): List<View> {
        val scrollableViews = mutableListOf<View>()

        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)

            when (child) {
                is ScrollView, is NestedScrollView -> {
                    Log.d("MobileSDK", "✅ Found ScrollView/NestedScrollView")
                    scrollableViews.add(child)
                }
                is androidx.recyclerview.widget.RecyclerView -> {
                    Log.d("MobileSDK", "✅ Found RecyclerView")
                    scrollableViews.add(child)
                }
                is ViewGroup -> {
                    scrollableViews.addAll(findScrollableViews(child, activity))
                }
            }
        }

        return scrollableViews
    }

    private fun setupScrollViewDetection(scrollView: View, activity: Activity) {
        if (viewsWithScrollListeners.containsKey(scrollView)) return
        viewsWithScrollListeners[scrollView] = true

        var localTriggered = false
        var lastScrollTime = 0L
        val scrollDebounceMs = 500L

        scrollView.viewTreeObserver.addOnScrollChangedListener {
            if (isInSurveyCooldown()) return@addOnScrollChangedListener
            if (localTriggered) return@addOnScrollChangedListener

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastScrollTime < scrollDebounceMs) return@addOnScrollChangedListener
            lastScrollTime = currentTime

            try {
                val scrollY = scrollView.scrollY
                val activeContext = currentContextName
                    ?: previousScreen
                    ?: activity.javaClass.simpleName.lowercase()

                val matchingSurveys = config.surveys.filter { survey ->
                    val meetsThreshold = scrollY >= survey.scrollThreshold
                    val lastFired = surveyTriggerTimestamps[survey.surveyId] ?: 0L
                    val isDebounced = (currentTime - lastFired) < 1500
                    val matchesScreen = survey.triggerScreens.isEmpty() ||
                            survey.triggerScreens.any {
                                activeContext.contains(it, ignoreCase = true)
                            }
                    val canShow = canShowSurvey(survey)

                    survey.enableScrollTrigger && meetsThreshold && matchesScreen && canShow && !isDebounced
                }

                if (matchingSurveys.isNotEmpty()) {
                    val bestSurvey = findHighestPrioritySurvey(matchingSurveys)
                    Log.d("MobileSDK", "🎯 Scroll Triggered! Locking Survey: ${bestSurvey.surveyId}")
                    surveyTriggerTimestamps[bestSurvey.surveyId] = currentTime
                    localTriggered = true
                    showSingleSurvey(activity, bestSurvey)
                }
            } catch (e: Exception) {
                Log.e("MobileSDK", "❌ ScrollView error: ${e.message}")
            }
        }
    }

    private fun setupRecyclerViewDetection(recyclerView: RecyclerView, activity: Activity) {
        if (viewsWithScrollListeners.containsKey(recyclerView)) return
        viewsWithScrollListeners[recyclerView] = true

        var localTriggered = false
        var lastScrollTime = 0L
        val scrollDebounceMs = 500L

        Log.d("MobileSDK", "✅ Attaching Scroll Listener to ${recyclerView.javaClass.simpleName}")

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (isInSurveyCooldown()) return
                if (localTriggered) return

                val currentTime = System.currentTimeMillis()
                if (currentTime - lastScrollTime < scrollDebounceMs) return
                lastScrollTime = currentTime

                try {
                    val scrollY = recyclerView.computeVerticalScrollOffset()
                    val activeContext = currentContextName
                        ?: previousScreen
                        ?: activity.javaClass.simpleName.lowercase()

                    val matchingSurveys = config.surveys.filter { survey ->
                        val meetsThreshold = scrollY >= survey.scrollThreshold
                        val lastFired = surveyTriggerTimestamps[survey.surveyId] ?: 0L
                        val isDebounced = (currentTime - lastFired) < 1500
                        val matchesScreen = survey.triggerScreens.isEmpty() ||
                                survey.triggerScreens.any {
                                    activeContext.contains(it, ignoreCase = true)
                                }
                        val canShow = canShowSurvey(survey)

                        survey.enableScrollTrigger && meetsThreshold && matchesScreen && canShow && !isDebounced
                    }

                    if (matchingSurveys.isNotEmpty()) {
                        val bestSurvey = findHighestPrioritySurvey(matchingSurveys)
                        Log.d("MobileSDK", "🎯 Scroll Triggered! Locking Survey: ${bestSurvey.surveyId}")
                        surveyTriggerTimestamps[bestSurvey.surveyId] = currentTime
                        localTriggered = true
                        showSingleSurvey(activity, bestSurvey)
                    }
                } catch (e: Exception) {
                    Log.e("MobileSDK", "❌ Recycler error: ${e.message}")
                }
            }
        })
    }

    // ====================================================================
    // PRIVATE METHODS - EXIT TRIGGERS & LIFECYCLE
    // ====================================================================
    private fun handleScreenExit(
        previousScreen: String,
        previousScreenLabel: String,
        newScreen: String,
        activity: Activity
    ) {
        Log.d("MobileSDK", "=== HANDLE SCREEN EXIT ===")
        Log.d("MobileSDK", "From: '$previousScreenLabel' → To: '$newScreen'")

        val allExitSurveys = config.surveys.filter { it.enableExitTrigger }
        Log.d("MobileSDK", "All exit surveys: ${allExitSurveys.size}")
        allExitSurveys.forEach { survey ->
            Log.d("MobileSDK", "  • ${survey.surveyId}: triggerScreens=${survey.triggerScreens}")
        }

        if (previousScreenLabel.contains("survey", ignoreCase = true) ||
            newScreen.contains("survey", ignoreCase = true)
        ) {
            Log.d("MobileSDK", "🔄 Skipping - survey screen involved")
            return
        }

        val screenExitSurveys = config.surveys.filter { survey ->
            val hasExitTrigger = survey.enableExitTrigger
            val hasTriggerScreens = survey.triggerScreens.isNotEmpty()
            val matchesScreen = survey.triggerScreens.any {
                previousScreenLabel.contains(it, ignoreCase = true)
            }
            val notTriggeredOnce = !(survey.triggerOnce &&
                    triggeredExits[survey.surveyId]?.contains(previousScreenLabel) == true)

            Log.d("MobileSDK", "🔍 Checking ${survey.surveyId}:")
            Log.d("MobileSDK", "   • hasExitTrigger: $hasExitTrigger")
            Log.d("MobileSDK", "   • hasTriggerScreens: $hasTriggerScreens")
            Log.d("MobileSDK", "   • triggerScreens: ${survey.triggerScreens}")
            Log.d("MobileSDK", "   • matches '$previousScreenLabel': $matchesScreen")
            Log.d("MobileSDK", "   • notTriggeredOnce: $notTriggeredOnce")
            Log.d("MobileSDK", "   • canShow: ${canShowSurvey(survey)}")

            hasExitTrigger && hasTriggerScreens && matchesScreen && notTriggeredOnce && canShowSurvey(survey)
        }

        Log.d("MobileSDK", "📊 Found ${screenExitSurveys.size} matching exit surveys")

        screenExitSurveys.forEach { survey ->
            Log.d("MobileSDK", "🎯 Showing SCREEN exit survey for leaving: $previousScreenLabel")
            triggeredExits[survey.surveyId]?.add(previousScreenLabel)
            showSingleSurvey(activity, survey)
        }
    }

    private fun createActivityLifecycleCallbacks(): Application.ActivityLifecycleCallbacks {
        return object : Application.ActivityLifecycleCallbacks {
            private var activitiesStarted = 0

            override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) {}

            override fun onActivityResumed(activity: Activity) {
                Log.d("MobileSDK", "🔄 RESUMED: ${activity.javaClass.simpleName}")

                if (!activity.javaClass.simpleName.contains("Survey", ignoreCase = true)) {
                    trackScreenView(activity)
                }
            }

            override fun onActivityPaused(activity: Activity) {
                lastPauseTime = System.currentTimeMillis()
            }

            override fun onActivityStarted(activity: Activity) {
                val activityKey = "${activity.javaClass.simpleName}_${System.identityHashCode(activity)}"
                if (!activity.javaClass.simpleName.contains("Survey", ignoreCase = true)) {
                    activeActivities.add(activityKey)
                }
                Log.d("MobileSDK", "🏁 STARTED: $activityKey")
            }

            override fun onActivityStopped(activity: Activity) {
                val activityKey = "${activity.javaClass.simpleName}_${System.identityHashCode(activity)}"
                if (!activity.javaClass.simpleName.contains("Survey", ignoreCase = true)) {
                    activeActivities.remove(activityKey)
                }

                if (activeActivities.isEmpty()) {
                    Log.d("MobileSDK", "✅ App Backgrounded")
                    handleAppExit(activity)
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        }
    }

    private fun handleAppExit(activity: Activity) {
        Log.d("MobileSDK", "=== APP EXIT DETECTED ===")

        val appExitSurveys = config.surveys.filter { survey ->
            survey.enableExitTrigger && survey.triggerScreens.isEmpty()
        }

        Log.d("MobileSDK", "📊 Saving ${appExitSurveys.size} APP exit surveys for welcome back")

        appExitSurveys.forEach { survey ->
            Log.d("MobileSDK", "💾 Saving: ${survey.surveyId}")
            savePendingExitSurvey(survey.surveyId)
        }
    }

    private fun savePendingExitSurvey(surveyId: String) {
        val prefs = context.getSharedPreferences("survey_sdk_exit", Context.MODE_PRIVATE)
        val pending = prefs.getStringSet("pending_surveys", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        pending.add(surveyId)
        prefs.edit().putStringSet("pending_surveys", pending).apply()
    }

    private fun getPendingExitSurveys(): Set<String> {
        val prefs = context.getSharedPreferences("survey_sdk_exit", Context.MODE_PRIVATE)
        return prefs.getStringSet("pending_surveys", emptySet()) ?: emptySet()
    }

    private fun clearPendingExitSurveys() {
        val prefs = context.getSharedPreferences("survey_sdk_exit", Context.MODE_PRIVATE)
        prefs.edit().remove("pending_surveys").apply()
    }

    private fun checkWelcomeBackSurveys(activity: Activity) {
        val pendingSurveys = getPendingExitSurveys().toList()
        if (pendingSurveys.isEmpty()) return       

        clearPendingExitSurveys()

        Log.d("MobileSDK", "🔄 Found ${pendingSurveys.size} pending welcome back surveys")

        Handler(Looper.getMainLooper()).postDelayed({
            pendingSurveys.forEach { surveyId ->
                val survey = config.surveys.find { it.surveyId == surveyId }
                survey?.let {
                    if (it.triggerScreens.isEmpty() && canShowSurvey(it)) {
                        Log.d("MobileSDK", "🎯 Showing WELCOME BACK survey: ${it.surveyId}")
                        triggeredExits[it.surveyId]?.add("app_exit")
                        showSingleSurvey(activity, it)
                        lastSurveyTime[it.surveyId] = System.currentTimeMillis()
                    }
                }
            }
        }, 1500)
    }

    private fun setupProcessLifecycle() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : LifecycleObserver {
            @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
            fun onAppBackgrounded() {
                Log.d("MobileSDK", "🎯 App went to background")
                lastActivity?.let { handleAppExit(it) }
            }

            @OnLifecycleEvent(Lifecycle.Event.ON_START)
            fun onAppForegrounded() {
                Log.d("MobileSDK", "🔄 App came to foreground")
                lastActivity?.let { checkWelcomeBackSurveys(it) }
            }
        })
    }

    private fun cleanupExitTrigger() {
        activityLifecycleCallbacks?.let {
            try {
                val application = context.applicationContext as? Application
                application?.unregisterActivityLifecycleCallbacks(it)
                activityLifecycleCallbacks = null
            } catch (e: Exception) {
                Log.e("MobileSDK", "Error cleaning up exit trigger")
            }
        }
    }

    // ====================================================================
    // PRIVATE METHODS - SURVEY DISPLAY
    // ====================================================================
    private fun showDialogSurvey(
        activity: Activity,
        url: String,
        allowedDomain: String?,
        animationType: String
    ) {
        try {
            Log.d("MobileSDK", "🚀 Launching SurveyDialogFragment")

            val dialogFragment = SurveyDialogFragment.newInstance(
                surveyUrl = url,
                backgroundColor = "#FFFFFF",
                animationType = animationType,
                allowedDomain = allowedDomain
            )

            if (activity is androidx.fragment.app.FragmentActivity) {
                dialogFragment.show(activity.supportFragmentManager, "SurveyDialogFragment")
            } else {
                Log.e(
                    "MobileSDK",
                    "❌ Activity is not FragmentActivity, cannot show dialog fragment"
                )
                showDialogActivityFallback(activity, url, allowedDomain, animationType)
            }

        } catch (e: Exception) {
            Log.e("MobileSDK", "❌ Dialog fragment failed: ${e.message}")
            showDialogActivityFallback(activity, url, allowedDomain, animationType)
        }
    }

    private fun showDialogActivityFallback(
        activity: Activity,
        url: String,
        allowedDomain: String?,
        animationType: String
    ) {
        try {
            Log.d("MobileSDK", "🔄 Using dialog fragment fallback")
            val dialogFragment = SurveyDialogFragment.newInstance(
                surveyUrl = url,
                backgroundColor = "#FFFFFF",
                animationType = animationType,
                allowedDomain = allowedDomain
            )
            if (activity is androidx.fragment.app.FragmentActivity) {
                dialogFragment.show(activity.supportFragmentManager, "SurveyDialogFragment")
            }
        } catch (e: Exception) {
            Log.e("MobileSDK", "❌ Dialog fragment fallback also failed: ${e.message}")
        }
    }

    private fun showBottomSheetSurvey(
        activity: Activity,
        url: String,
        allowedDomain: String?,
        animationType: String,
        backgroundColor: String
    ) {
        try {
            Log.d("MobileSDK", "🚀 Launching SurveyBottomSheetFragment")

            if (activity is androidx.fragment.app.FragmentActivity) {
                val bottomSheetFragment = SurveyBottomSheetFragment.newInstance(
                    surveyUrl = url,
                    backgroundColor = backgroundColor,
                    allowedDomain = allowedDomain
                )
                bottomSheetFragment.show(
                    activity.supportFragmentManager,
                    "SurveyBottomSheetFragment"
                )
            } else {
                Log.e("MobileSDK", "❌ Activity is not FragmentActivity, using activity fallback")
                showBottomSheetActivityFallback(
                    activity,
                    url,
                    allowedDomain,
                    animationType,
                    backgroundColor
                )
            }

        } catch (e: Exception) {
            Log.e("MobileSDK", "❌ Bottom sheet fragment failed: ${e.message}")
            showBottomSheetActivityFallback(
                activity,
                url,
                allowedDomain,
                animationType,
                backgroundColor
            )
        }
    }

    private fun showBottomSheetActivityFallback(
        activity: Activity,
        url: String,
        allowedDomain: String?,
        animationType: String,
        backgroundColor: String
    ) {
        try {
            Log.d("MobileSDK", "🔄 Using bottom sheet fragment fallback")
            if (activity is androidx.fragment.app.FragmentActivity) {
                val bottomSheetFragment = SurveyBottomSheetFragment.newInstance(
                    surveyUrl = url,
                    backgroundColor = backgroundColor,
                    allowedDomain = allowedDomain
                )
                bottomSheetFragment.show(
                    activity.supportFragmentManager,
                    "SurveyBottomSheetFragment"
                )
            }
        } catch (e: Exception) {
            Log.e("MobileSDK", "❌ Bottom sheet fragment fallback failed: ${e.message}")
        }
    }

    private fun showFullScreenSurvey(
        activity: Activity,
        url: String,
        allowedDomain: String?,
        animationType: String,
        backgroundColor: String
    ) {
        try {
            Log.d("MobileSDK", "🚀 Launching SurveyFullScreenActivity")
            val intent = Intent(activity, SurveyFullScreenActivity::class.java).apply {
                putExtra("SURVEY_URL", url)
                putExtra("BACKGROUND_COLOR", backgroundColor)
                putExtra("ANIMATION_TYPE", animationType)
                putExtra("ALLOWED_DOMAIN", allowedDomain)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e("MobileSDK", "❌ Full screen survey failed: ${e.message}")
        }
    }

    // ====================================================================
    // PRIVATE METHODS - UTILITIES
    // ====================================================================
    private fun isInSurveyCooldown(): Boolean {
        val timeSinceClose = System.currentTimeMillis() - lastSurveyClosedTime
        if (timeSinceClose < SURVEY_CLOSE_COOLDOWN_MS) {
            Log.d("MobileSDK", "🛡️ Ignoring trigger - Post-survey cooldown active (${timeSinceClose}ms)")
            return true
        }
        return false
    }
}