package com.c4f.mobileSDK

/**
 * Central constants for the Survey SDK.
 * Update SDK_VERSION when releasing a new version.
 */
object SDKConstants {
    // ===== SDK VERSION =====
    const val SDK_VERSION = "2.0.19"
    const val SDK_NAME = "MobileSDK"

    // ===== API CONFIGURATION =====
    const val API_TIMEOUT_MS = 10000L
    const val API_MAX_RETRIES = 2
    const val CACHE_DURATION_HOURS = 24L

    // ===== UI DEFAULTS =====
    const val DEFAULT_ANIMATION = "slide_up"
    const val DEFAULT_MODAL_STYLE = "full_screen"
    const val DEFAULT_BACKGROUND_COLOR = "#FFFFFF"

    // ===== TRIGGER DEFAULTS =====
    const val DEFAULT_SCROLL_THRESHOLD = 500
    const val DEFAULT_TIME_DELAY = 0L
    const val DEFAULT_PROBABILITY = 1.0
    const val DEFAULT_PRIORITY = 1

    // ===== BUTTON DETECTION =====
    val PREDEFINED_BUTTON_IDS = listOf(
        "survey_button",
        "take_survey",
        "feedback_button",
        "rate_app"
    )

    // ===== ERROR MESSAGES =====
    const val ERROR_NOT_INITIALIZED = "MobileSDK not initialized. Call initialize() first."
    const val ERROR_CONFIG_NOT_LOADED = "Configuration not loaded."

    // ===== LOG TAGS =====
    const val LOG_TAG_SDK = "MobileSDK"
    const val LOG_TAG_API = "SurveyApiService"
    const val LOG_TAG_CACHE = "ConfigCache"
    const val LOG_TAG_WEBVIEW = "WebViewConfigurator"

    // ===== TIMING CONSTANTS =====
    const val AUTO_SETUP_DELAY_MS = 300L
    const val BUTTON_DEBOUNCE_MS = 1000L
    const val SURVEY_QUEUE_DELAY_MS = 500L
}