package com.c4f.mobileSDK.android

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import com.c4f.mobileSDK.MobileSDK
import com.c4f.mobileSDK.core.SurveyPlatform

class AndroidMobileSDK(
    private val context: Application,
    private val apiKey: String,
    private val paramName: String? = null,
    private val paramValue: String? = null
) : SurveyPlatform {

    private val mobileSDK: MobileSDK by lazy {
        // when {
        //     paramName != null && paramValue != null -> 
        //         MobileSDK.initialize(context, apiKey, paramName to paramValue)
        //     paramName != null -> 
        //         MobileSDK.initialize(context, apiKey, paramName)
        //     else -> 
        //         MobileSDK.initialize(context, apiKey)
        // }
        MobileSDK.getInstance()
    }

    fun initializeCoreSDK() {
        val isEnabled = mobileSDK.isSDKEnabled()
        Log.d("AndroidMobileSDK", "Core SDK initialized. Enabled: $isEnabled")
    }   
    
    /**
     * Get current SDK parameters
     */
    fun getCurrentParameters(): Map<String, String> {
        return MobileSDK.getCurrentParameters()
    }

    override fun showSurvey() {
        throw IllegalStateException("Use showSurveyInActivity(activity) for React Native")
    }

    override fun setUserProperty(key: String, value: String) {
        context.getSharedPreferences("survey_sdk_data", Context.MODE_PRIVATE)
            .edit()
            .putString(key, value)
            .apply()
    }

    override fun setCustomParam(name: String, value: String) {
        context.getSharedPreferences("survey_sdk_data", Context.MODE_PRIVATE)
            .edit()
            .putString(name, value)
            .apply()
        Log.d("AndroidMobileSDK", "Custom param set: $name=$value")
    }

    override fun trackEvent(eventName: String, properties: Map<String, Any>) {
        Log.d("MobileSDK", "Event tracked: $eventName, Properties: $properties")
    }

    override fun setApiKey(apiKey: String) {
        // Do nothing - API key is set in constructor
    }

    // --- Core SDK Methods ---

    fun autoSetup(activity: Activity) {
        mobileSDK.autoSetup(activity)
    }

    fun showSurveyInActivity(activity: Activity) {
        mobileSDK.showSurvey(activity)
    }

    fun showSurveyByIdInActivity(activity: Activity, surveyId: String) {
        mobileSDK.showSurveyById(activity, surveyId)
    }

    fun isUserExcluded(surveyId: String): Boolean {
        return mobileSDK.isUserExcluded(surveyId)
    }

    fun getDebugStatus(): String {
        return mobileSDK.debugSurveyStatus()
    }
    
    fun setupButtonTrigger(buttonId: Int, activity: Activity) {
        mobileSDK.setupButtonTrigger(buttonId, activity, null)
    }
    
    fun setupButtonTrigger(buttonId: Int, activity: Activity, surveyId: String) {
        mobileSDK.setupButtonTrigger(buttonId, activity, surveyId)
    }
    
    fun triggerButtonByStringId(buttonId: String, activity: Activity) {
        mobileSDK.triggerButtonByStringId(buttonId, activity)
    }
    
    fun triggerByNavigation(screenName: String, activity: Activity) {
        mobileSDK.triggerByNavigation(screenName, activity)
    }
    
    fun triggerByTabChange(tabName: String, activity: Activity) {
        mobileSDK.triggerByTabChange(tabName, activity)
    }

    
    fun autoSetupSafe(activity: Activity) {
        mobileSDK.enableNavigationSafety().autoSetup(activity)
    }

    fun triggerScrollManual(activity: Activity, scrollY: Int = 500) {
        mobileSDK.triggerScrollManual(activity, scrollY)
    }
}