package com.c4f.mobileSDK

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import com.c4f.mobileSDK.core.SurveyPlatform
import com.c4f.mobileSDK.android.AndroidMobileSDK

class UniversalMobileSDK private constructor() {
    companion object {
        @Volatile private var instance: UniversalMobileSDK? = null

        fun getInstance(): UniversalMobileSDK {
            return instance ?: synchronized(this) {
                instance ?: UniversalMobileSDK().also { instance = it }
            }
        }
    }

    init {
        Log.d("UniversalMobileSDK_RN", "🚨 UNIVERSAL MODULE LOADED!")
    }

    private var platform: SurveyPlatform? = null
    private var isInitialized = false
    private var androidSDK: AndroidMobileSDK? = null

    // ====================================================================
    // INITIALIZATION METHODS (UPDATED)
    // ====================================================================

    // SIMPLE: Just one initialization method
    fun initialize(application: Application, apiKey: String, vararg params: Any) {
    Log.d("UniversalMobileSDK_RN", "🚨 UNIVERSAL INITIALIZE CALLED!")
        if (!isInitialized) {
            Log.d("UniversalMobileSDK", "Initializing with API key")
            
            // Pass through to the real SDK
            MobileSDK.initialize(application, apiKey, *params)
            
            // 🛡️ Enable Hybrid Mode here!
            MobileSDK.getInstance().enableHybridMode()
            
            androidSDK = AndroidMobileSDK(application, apiKey)
            platform = androidSDK
            isInitialized = true
        }
    }

    // ====================================================================
    // SURVEY DISPLAY METHODS
    // ====================================================================

    /**
     * Show survey
     */
    fun showSurvey(activity: Activity) {
        checkInitialized()
        androidSDK?.showSurveyInActivity(activity)
    }

    /**
     * Show specific survey by ID
     */
    fun showSurveyById(activity: Activity, surveyId: String) {
        checkInitialized()
        androidSDK?.showSurveyByIdInActivity(activity, surveyId)
    }

    /**
     * Auto setup
     */
    fun autoSetup(activity: Activity) {
        checkInitialized()
        androidSDK?.autoSetup(activity)
    }

    /**
     * Auto setup with navigation safety
     */
    fun autoSetupSafe(activity: Activity) {
        checkInitialized()
        // Check if AndroidSDK has this method, if not, we need to add it
        androidSDK?.let {
            // We'll need to add autoSetupSafe() to AndroidMobileSDK
            MobileSDK.getInstance().enableNavigationSafety().autoSetup(activity)
        }
    }

    // ====================================================================
    // TRIGGER METHODS (NEW)
    // ====================================================================

    /**
     * Trigger button survey
     */
    fun triggerButtonByStringId(buttonId: String, activity: Activity) {
        checkInitialized()
        androidSDK?.triggerButtonByStringId(buttonId, activity)
    }

    /**
     * Trigger navigation survey
     */
    fun triggerByNavigation(screenName: String, activity: Activity) {
        checkInitialized()
        androidSDK?.triggerByNavigation(screenName, activity)
    }

    /**
     * Trigger tab change survey
     */
    fun triggerByTabChange(tabName: String, activity: Activity) {
        checkInitialized()
        androidSDK?.triggerByTabChange(tabName, activity)
    }

    /**
     * Trigger scroll survey
     */
    fun triggerScrollManual(activity: Activity, scrollY: Int = 500) {
        checkInitialized()
        androidSDK?.let {
            // AndroidMobileSDK needs this method
            MobileSDK.getInstance().triggerScrollManual(activity, scrollY)
        }
    }

    // ====================================================================
    // USER PROPERTIES & EVENTS
    // ====================================================================

    /**
     * Set user property
     */
    fun setUserProperty(key: String, value: String) {
        checkInitialized()
        platform?.setUserProperty(key, value)
    }

    /**
     * Set custom parameter
     */
    fun setCustomParam(name: String, value: String) {
        checkInitialized()
        platform?.setCustomParam(name, value)
    }

    /**
     * Track event
     */
    fun trackEvent(eventName: String, properties: Map<String, Any> = emptyMap()) {
        checkInitialized()
        platform?.trackEvent(eventName, properties)
    }

    // ====================================================================
    // NAVIGATION SAFETY (NEW)
    // ====================================================================

    /**
     * Enable navigation safety mode
     */
    fun enableNavigationSafety(): UniversalMobileSDK {
        checkInitialized()
        MobileSDK.getInstance().enableNavigationSafety()
        return this
    }

    // ====================================================================
    // DEBUG & STATUS METHODS
    // ====================================================================

    /**
     * Get current parameters
     */
    fun getCurrentParameters(): Map<String, String> {
        checkInitialized()
        return androidSDK?.getCurrentParameters() ?: emptyMap()
    }
    
    /**
     * Get debug status
     */
    fun getDebugStatus(): String {
        checkInitialized()
        return androidSDK?.getDebugStatus() ?: "Platform not available"
    }
    
    /**
     * Check if user is excluded
     */
    fun isUserExcluded(surveyId: String? = null): Boolean {
        checkInitialized()
        return if (surveyId != null) {
            androidSDK?.isUserExcluded(surveyId) ?: false
        } else {
            MobileSDK.getInstance().isUserExcluded()
        }
    }
    
    /**
     * Setup button trigger
     */
    fun setupButtonTrigger(buttonId: Int, activity: Activity) {
        checkInitialized()
        androidSDK?.setupButtonTrigger(buttonId, activity)
    }

    fun setupButtonTrigger(buttonId: Int, activity: Activity, surveyId: String) {
        checkInitialized()
        androidSDK?.setupButtonTrigger(buttonId, activity, surveyId)
    }

    // ====================================================================
    // REINITIALIZATION METHODS
    // ====================================================================
    
    /**
     * Reinitialize
     */
    fun reinitialize(context: Context): Boolean {
        checkInitialized()
        
        try {
            return MobileSDK.forceReinitialize(context)
        } catch (e: Exception) {
            Log.e("UniversalMobileSDK", "Reinitialization failed: ${e.message}")
            return false
        }
    }
    
    /**
     * Reinitialize with parameters
     */
    fun reinitializeWithParameters(context: Context, apiKey: String, paramName: String, paramValue: String): Boolean {
        try {
            // Reset and create new platform
            androidSDK = AndroidMobileSDK(
                context.applicationContext as Application,
                apiKey,
                paramName,
                paramValue
            )
            platform = androidSDK
            isInitialized = true
            
            // Force reinitialize core SDK
            return MobileSDK.forceReinitialize(context)
        } catch (e: Exception) {
            Log.e("UniversalMobileSDK", "Reinitialization failed: ${e.message}")
            return false
        }
    }
    
    /**
     * Reinitialize with parameter name
     */
    fun reinitializeWithParameterName(context: Context, apiKey: String, paramName: String): Boolean {
        try {
            androidSDK = AndroidMobileSDK(
                context.applicationContext as Application,
                apiKey,
                paramName,
                null
            )
            platform = androidSDK
            isInitialized = true
            
            return MobileSDK.forceReinitialize(context)
        } catch (e: Exception) {
            Log.e("UniversalMobileSDK", "Reinitialization failed: ${e.message}")
            return false
        }
    }

    // ====================================================================
    // UTILITY METHODS
    // ====================================================================

    fun getPlatform(): SurveyPlatform? = platform
    fun isInitialized(): Boolean = isInitialized

    fun getAndroidSDK(): AndroidMobileSDK? = androidSDK

    /**
     * Check if SDK is ready
     */
    fun isReady(): Boolean {
        return isInitialized && MobileSDK.getInstance().isReady()
    }

    /**
     * Get setup status
     */
    fun getSetupStatus(): String {
        return if (isInitialized) {
            "UniversalMobileSDK Status:\n" +
            "- Initialized: $isInitialized\n" +
            "- AndroidSDK: ${androidSDK != null}\n" +
            "- Platform: ${platform?.javaClass?.simpleName ?: "null"}\n" +
            "- Core SDK Ready: ${MobileSDK.getInstance().isReady()}"
        } else {
            "UniversalMobileSDK not initialized"
        }
    }

    // ====================================================================
    // PRIVATE HELPERS
    // ====================================================================

    private fun checkInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("MobileSDK not initialized. Call initialize() first.")
        }
    }
}