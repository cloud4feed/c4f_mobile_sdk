package com.c4f.mobileSDK.core

interface SurveyPlatform {
    fun showSurvey()
    fun setUserProperty(key: String, value: String)
    fun trackEvent(eventName: String, properties: Map<String, Any> = emptyMap())
    fun setApiKey(apiKey: String)
    // ✅ Consider adding parameter methods if needed by other platforms
    fun setCustomParam(name: String, value: String) // Optional
}