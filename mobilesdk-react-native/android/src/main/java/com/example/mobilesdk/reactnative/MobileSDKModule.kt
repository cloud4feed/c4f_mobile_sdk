package com.c4f.mobileSDK.reactnative

import com.facebook.react.bridge.*
import com.facebook.react.module.annotations.ReactModule
import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.c4f.mobileSDK.MobileSDK
import com.c4f.mobileSDK.UniversalMobileSDK
import kotlinx.coroutines.*
import java.lang.ref.WeakReference
import androidx.annotation.Nullable
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.Promise

@ReactModule(name = "MobileSDK")
class MobileSDKModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    
    private var currentActivity: WeakReference<Activity>? = null
    private var isAutoSetupComplete = false
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var registeredReactButtons = mutableSetOf<String>()
    private var scrollDetectionEnabled = false
    private var hasNavigationListener = false

    
    private var activityDelegate: MobileSDKActivityDelegate? = null

    
    init {
        Log.d("MobileSDK_RN_CRITICAL", "=== MODULE LOADED ===")
        Log.d("MobileSDK_RN_CRITICAL", "Class: ${this::class.java.name}")
        
        currentActivity = WeakReference(reactContext.currentActivity)
        
        // NEW: Initialize the activity delegate
        activityDelegate = MobileSDKActivityDelegate(reactContext)
        activityDelegate?.initialize()

        // Print ALL methods this module exposes
        val methods = this::class.java.methods
        methods.filter { it.declaringClass == this::class.java }
            .filter { it.name == "initialize" || it.name.contains("initialize") }
            .forEach {
                Log.d("MobileSDK_RN_CRITICAL", "📌 EXPOSED METHOD: ${it.name}")
                Log.d("MobileSDK_RN_CRITICAL", "   Parameters: ${it.parameterTypes.joinToString { it.simpleName }}")
            }
    }

    override fun getName(): String = "MobileSDK"

    // ✅ FOR SIMPLE INIT - NO PARAMS ARRAY
    @ReactMethod
    fun initialize(apiKey: String, promise: Promise) {
        Log.d("MobileSDK_RN", "✅ initialize")
        try {
            Log.d("MobileSDK_RN", "✅ initialize(apiKey) called")
            
            val activity = getCurrentActivitySafely()
            if (activity == null) {
                promise.reject("NO_ACTIVITY", "No activity available")
                return
            }
            
            val application = activity.application
            UniversalMobileSDK.getInstance().initialize(application, apiKey)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("INIT_ERROR", e.message)
        }
    }

    // ✅ FOR INIT WITH PARAMETERS
    @ReactMethod
    fun initializeWithParams(apiKey: String, params: ReadableArray, promise: Promise) {
        Log.d("MobileSDK_RN", "✅ initializeWithParams")
        try {
            Log.d("MobileSDK_RN", "✅ initializeWithParams called")
            
            val activity = getCurrentActivitySafely()
            if (activity == null) {
                promise.reject("NO_ACTIVITY", "No activity available")
                return
            }
            
            val application = activity.application
            val anyParams = convertToAnyArray(params)
            UniversalMobileSDK.getInstance().initialize(application, apiKey, *anyParams)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("INIT_ERROR", e.message)
        }
    }

    private fun convertToAnyArray(params: ReadableArray): Array<Any> {
        val result = mutableListOf<Any>()
        
        for (i in 0 until params.size()) {
            when (params.getType(i)) {
                ReadableType.String -> {
                    params.getString(i)?.let { result.add(it) }
                }
                ReadableType.Map -> {
                    val map = params.getMap(i)
                    map?.keySetIterator()?.let { iterator ->
                        while (iterator.hasNextKey()) {
                            val key = iterator.nextKey()
                            val value = map.getString(key)
                            if (key != null && value != null) {
                                result.add(Pair(key, value))
                            }
                        }
                    }
                }
                else -> {
                    Log.w("MobileSDK_RN", "⚠️ Skipping unsupported type: ${params.getType(i)}")
                }
            }
        }
        
        return result.toTypedArray()
    }

    @ReactMethod
    fun autoSetup(promise: Promise) {
        try {
            val activity = getCurrentActivitySafely()
            if (activity == null) {
                promise.reject("NO_ACTIVITY", "No activity available")
                return
            }
            
            activity.runOnUiThread {
                MobileSDK.getInstance().autoSetup(activity)
                promise.resolve(true)
            }
        } catch (e: Exception) {
            promise.reject("SETUP_ERROR", e.message)
        }
    }

    // Helper method to get current activity (use delegate first)
    fun getCurrentActivitySafely(): Activity? {
        // First try the delegate (it tracks ReactActivity lifecycle)
        activityDelegate?.getCurrentActivity()?.let { return it }
        // Fallback to the default method
        return super.getCurrentActivity()
    }

    @ReactMethod
    fun autoSetupReact(promise: Promise) {
        try {
            Log.d("MobileSDK_RN", "🔄 RN: Starting autoSetup...")
            
            val activity = getCurrentActivitySafely()
            if (activity == null) {
                promise.reject("NO_ACTIVITY", "No activity available")
                return
            }
            
            activity.runOnUiThread {
                try {
                    val mobileSDK = MobileSDK.getInstance()
                    
                    // Let core SDK do its normal autoSetup (Hybrid Mode handles the rest)
                    mobileSDK.autoSetup(activity)

                    activityDelegate?.setupGlobalTouchInterceptor(activity)
                    
                    isAutoSetupComplete = true
                    Log.d("MobileSDK_RN", "✅ React Native auto setup completed. Native UI scanning disabled.")
                    
                    promise.resolve(true)
                } catch (e: Exception) {
                    promise.reject("SETUP_ERROR", "Auto setup failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            promise.reject("SETUP_ERROR", "Auto setup failed: ${e.message}")
        }
    }

    private fun retryNavigationDetection(mobileSDK: MobileSDK, activity: Activity, attempt: Int) {
        if (attempt > 3) return // Max 3 retries
        
        val delay = when (attempt) {
            0 -> 1000L  // 1 second
            1 -> 2000L  // 2 seconds
            2 -> 3000L  // 3 seconds
            else -> 5000L
        }
        
        activity.window?.decorView?.postDelayed({
            try {
                Log.d("MobileSDK_RN", "🔄 Retry $attempt: Re-triggering navigation detection")
                mobileSDK.reSetupNavigationDetection(activity)
            } catch (e: Exception) {
                Log.e("MobileSDK_RN", "Navigation re-trigger failed on retry $attempt", e)
                // Try again with next attempt
                retryNavigationDetection(mobileSDK, activity, attempt + 1)
            }
        }, delay)
    }

    // ====================================================================
    // AUTO DETECTION - SDK WILL DETECT EVERYTHING AUTOMATICALLY
    // ====================================================================

    private fun setupAutoDetection(activity: Activity) {
        try {
            Log.d("MobileSDK_RN", "🔍 Setting up React Native auto-detection...")
            
            val rootView = activity.window.decorView          
                       
            Log.d("MobileSDK_RN", "✅ React Native auto-detection ready")
            Log.d("MobileSDK_RN", "🎯 SDK will automatically detect:")
            Log.d("MobileSDK_RN", "   • Bottom navigation tabs")
            Log.d("MobileSDK_RN", "   • In-page tabs (Mens, Womens, etc.)")
            Log.d("MobileSDK_RN", "   • Button clicks")
            Log.d("MobileSDK_RN", "   • Screen navigation")
            
        } catch (e: Exception) {
            Log.e("MobileSDK_RN", "❌ Auto-detection setup failed", e)
        }
    }

    // Add these methods to your MobileSDKModule.kt

    @ReactMethod
    fun trackScreenView(screenName: String, promise: Promise) {
        try {
            Log.d("MobileSDK_RN", "📱 Auto screen tracking: $screenName")
            val activity = getCurrentActivitySafely()
            if (activity != null) {
                // Call the core SDK's trackScreenView with the screen name
                MobileSDK.getInstance().trackScreenView(screenName, activity)
                promise.resolve(true)
            } else {
                // If no activity, still resolve true (don't break the app)
                Log.e("MobileSDK_RN", "No activity available for screen tracking")
                promise.resolve(false)
            }
        } catch (e: Exception) {
            Log.e("MobileSDK_RN", "Error in trackScreenView: ${e.message}")
            promise.reject("TRACKING_ERROR", e.message)
        }
    }

    @ReactMethod
    fun triggerExitSurvey(screenName: String, promise: Promise) {
        try {
            Log.d("MobileSDK_RN", "🚪 Trigger exit survey for: $screenName")
            val activity = getCurrentActivitySafely()
            if (activity != null) {
                // This specifically triggers exit surveys
                // You might need a specific method in your core SDK
                // For now, we can use triggerByNavigation which handles both
                MobileSDK.getInstance().triggerByNavigation(screenName, activity)
                promise.resolve(true)
            } else {
                promise.reject("NO_ACTIVITY", "No activity available")
            }
        } catch (e: Exception) {
            promise.reject("ERROR", e.message)
        }
    }

    // Add this method to MobileSDKModule.kt temporarily
    @ReactMethod
    fun forceLogStatus(promise: Promise) {
        try {
            Log.d("MobileSDK_RN_DEBUG", "=== FORCED STATUS CHECK ===")
            
            val activity = getCurrentActivitySafely()
            if (activity != null) {
                Log.d("MobileSDK_RN_DEBUG", "Current activity: ${activity::class.java.simpleName}")
            }
            
            val mobileSDK = MobileSDK.getInstance()
            val isEnabled = mobileSDK.isSDKEnabled()
            Log.d("MobileSDK_RN_DEBUG", "SDK Enabled: $isEnabled")
            
            val configLoaded = mobileSDK.isConfigurationLoaded()
            Log.d("MobileSDK_RN_DEBUG", "Config loaded: $configLoaded")
            
            // Force a log from the core SDK
            mobileSDK.debugConfigStatus()
            
            promise.resolve("Status logged")
        } catch (e: Exception) {
            promise.reject("DEBUG_ERROR", e.message)
        }
    }

    // ====================================================================
    // TRIGGER METHODS
    // ====================================================================
    @ReactMethod
    fun triggerButtonSurvey(buttonId: String, promise: Promise) {
        try {
            Log.d("MobileSDK_RN", "🎯 RN Bridge: Manual trigger for button: $buttonId")
            val activity = getCurrentActivitySafely()
            if (activity != null) {
                MobileSDK.getInstance().triggerButtonByStringId(buttonId, activity)
                promise.resolve(true)
            } else {
                promise.reject("NO_ACTIVITY", "No activity available")
            }
        } catch (e: Exception) {
            promise.reject("TRIGGER_ERROR", "Failed to trigger survey: ${e.message}")
        }
    }

    @ReactMethod
    fun triggerScrollSurvey(promise: Promise) {
        try {
            Log.d("MobileSDK_RN", "📜 RN Bridge: Manual scroll trigger")
            val activity = getCurrentActivitySafely()
            if (activity != null) {
                MobileSDK.getInstance().triggerScrollManual(activity, 1000)
                promise.resolve(true)
            } else {
                promise.reject("NO_ACTIVITY", "No activity available")
            }
        } catch (e: Exception) {
            promise.reject("TRIGGER_ERROR", "Failed to trigger scroll survey: ${e.message}")
        }
    }

    @ReactMethod
    fun triggerNavigationSurvey(screenName: String, promise: Promise) {
        try {
            Log.d("MobileSDK_RN", "📍 RN Bridge: Manual navigation trigger: $screenName")
            val activity = getCurrentActivitySafely()
            if (activity != null) {
                MobileSDK.getInstance().triggerByNavigation(screenName, activity)
                promise.resolve(true)
            } else {
                promise.reject("NO_ACTIVITY", "No activity available")
            }
        } catch (e: Exception) {
            promise.reject("TRIGGER_ERROR", "Failed to trigger navigation survey: ${e.message}")
        }
    }

    @ReactMethod
    fun triggerByTabChange(tabName: String, promise: Promise) {
        try {
            Log.d("MobileSDK_RN", "📍 RN Bridge: Manual tab trigger: $tabName")
            val activity = getCurrentActivitySafely()
            if (activity != null) {
                MobileSDK.getInstance().triggerByTabChange(tabName, activity)
                promise.resolve(true)
            } else {
                promise.reject("NO_ACTIVITY", "No activity available")
            }
        } catch (e: Exception) {
            promise.reject("TRIGGER_ERROR", "Failed to trigger tab survey: ${e.message}")
        }
    }

    @ReactMethod
    fun enableNavigationSafety(promise: Promise) {
        try {
            MobileSDK.getInstance().enableNavigationSafety()
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("SAFETY_ERROR", "Failed to enable navigation safety")
        }
    }

    @ReactMethod
    fun autoSetupSafe(promise: Promise) {
        try {
            val activity = getCurrentActivitySafely()
            if (activity != null) {
                MobileSDK.getInstance().autoSetupSafe(activity)
                promise.resolve(true)
            } else {
                promise.reject("NO_ACTIVITY", "No activity available")
            }
        } catch (e: Exception) {
            promise.reject("SETUP_ERROR", "Failed to safe auto setup")
        }
    }

    // ====================================================================
    // SURVEY DISPLAY METHODS
    // ====================================================================
    @ReactMethod
    fun showSurvey(promise: Promise) {
        try {
            val activity = getCurrentActivitySafely()
            if (activity != null) {
                MobileSDK.getInstance().showSurvey(activity)
                promise.resolve(true)
            } else {
                promise.reject("NO_ACTIVITY", "No activity available")
            }
        } catch (e: Exception) {
            promise.reject("SHOW_ERROR", "Failed to show survey: ${e.message}")
        }
    }

    @ReactMethod
    fun showSurveyById(surveyId: String, promise: Promise) {
        try {
            val activity = getCurrentActivitySafely()
            if (activity != null) {
                MobileSDK.getInstance().showSurveyById(activity, surveyId)
                promise.resolve(true)
            } else {
                promise.reject("NO_ACTIVITY", "No activity available")
            }
        } catch (e: Exception) {
            promise.reject("SHOW_ERROR", "Failed to show survey $surveyId: ${e.message}")
        }
    }

    // ====================================================================
    // USER PROPERTIES
    // ====================================================================
    @ReactMethod
    fun setUserProperty(key: String, value: String, promise: Promise) {
        try {
            val activity = getCurrentActivitySafely()
            if (activity != null) {
                activity.getSharedPreferences("survey_sdk_data", Context.MODE_PRIVATE)
                    .edit().putString(key, value).apply()
                promise.resolve(true)
            } else {
                promise.reject("NO_ACTIVITY", "No current activity available")
            }
        } catch (e: Exception) {
            promise.reject("PROPERTY_ERROR", "Failed to set user property: ${e.message}")
        }
    }

    @ReactMethod
    fun setSessionData(key: String, value: String, promise: Promise) {
        try {
            MobileSDK.getInstance().setSessionData(key, value)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("SESSION_ERROR", "Failed to set session data: ${e.message}")
        }
    }

    @ReactMethod
    fun resetSessionData(promise: Promise) {
        try {
            MobileSDK.getInstance().resetSessionData()
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("SESSION_ERROR", "Failed to reset session data: ${e.message}")
        }
    }

    // ====================================================================
    // STATUS & DEBUG METHODS
    // ====================================================================
    @ReactMethod
    fun isUserExcluded(promise: Promise) {
        try {
            promise.resolve(MobileSDK.getInstance().isUserExcluded())
        } catch (e: Exception) {
            promise.reject("EXCLUSION_ERROR", "Failed to check exclusion: ${e.message}")
        }
    }

    @ReactMethod
    fun isUserExcludedForSurvey(surveyId: String, promise: Promise) {
        try {
            promise.resolve(MobileSDK.getInstance().isUserExcluded(surveyId))
        } catch (e: Exception) {
            promise.reject("EXCLUSION_ERROR", "Failed to check exclusion for survey $surveyId: ${e.message}")
        }
    }

    @ReactMethod
    fun getDebugStatus(promise: Promise) {
        try {
            promise.resolve(MobileSDK.getInstance().debugSurveyStatus())
        } catch (e: Exception) {
            promise.reject("DEBUG_ERROR", "Failed to get debug status: ${e.message}")
        }
    }

    @ReactMethod
    fun getSurveyIds(promise: Promise) {
        try {
            val surveyIds = MobileSDK.getInstance().getSurveyIds()
            val writableArray = Arguments.createArray()
            surveyIds.forEach { writableArray.pushString(it) }
            promise.resolve(writableArray)
        } catch (e: Exception) {
            promise.reject("CONFIG_ERROR", "Failed to get survey IDs: ${e.message}")
        }
    }

    @ReactMethod
    fun isConfigurationLoaded(promise: Promise) {
        try {
            promise.resolve(MobileSDK.getInstance().isConfigurationLoaded())
        } catch (e: Exception) {
            promise.reject("CONFIG_ERROR", "Failed to check configuration status: ${e.message}")
        }
    }

    @ReactMethod
    fun getQueueStatus(promise: Promise) {
        try {
            promise.resolve(MobileSDK.getInstance().getQueueStatus())
        } catch (e: Exception) {
            promise.reject("QUEUE_ERROR", "Failed to get queue status: ${e.message}")
        }
    }

    @ReactMethod
    fun clearSurveyQueue(promise: Promise) {
        try {
            MobileSDK.getInstance().clearSurveyQueue()
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("QUEUE_ERROR", "Failed to clear survey queue: ${e.message}")
        }
    }

    @ReactMethod
    fun isShowingSurvey(promise: Promise) {
        try {
            promise.resolve(MobileSDK.getInstance().isShowingSurvey())
        } catch (e: Exception) {
            promise.reject("SURVEY_ERROR", "Failed to check if survey is showing: ${e.message}")
        }
    }

    @ReactMethod
    fun isSDKEnabled(promise: Promise) {
        try {
            promise.resolve(MobileSDK.getInstance().isSDKEnabled())
        } catch (e: Exception) {
            promise.reject("STATUS_ERROR", "Failed to check if SDK is enabled: ${e.message}")
        }
    }

    @ReactMethod
    fun fetchConfiguration(promise: Promise) {
        try {
            MobileSDK.getInstance().fetchConfiguration()
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("CONFIG_ERROR", "Failed to fetch configuration: ${e.message}")
        }
    }

    @ReactMethod
    fun getConfigForDebug(promise: Promise) {
        try {
            promise.resolve(MobileSDK.getInstance().getConfigForDebug())
        } catch (e: Exception) {
            promise.reject("CONFIG_ERROR", "Failed to get config debug info: ${e.message}")
        }
    }

    @ReactMethod
    fun resetTriggers(promise: Promise) {
        try {
            MobileSDK.getInstance().resetTriggers()
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("TRIGGER_ERROR", "Failed to reset triggers: ${e.message}")
        }
    }

    @ReactMethod
    fun cleanup(promise: Promise) {
        try {
            MobileSDK.getInstance().cleanup()
            coroutineScope.cancel()
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("CLEANUP_ERROR", "Failed to cleanup SDK: ${e.message}")
        }
    }

    override fun onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy()
        coroutineScope.cancel()
    }
}