package com.c4f.mobileSDK

object DefaultConfig {

    // ===== EMPTY CONFIGURATION =====
    val EMPTY_CONFIG = Config(
        sdkVersion = SDKConstants.SDK_VERSION,
        cacheDurationHours = SDKConstants.CACHE_DURATION_HOURS,
        surveys = emptyList()
    )

    // ===== TEST CONFIGURATION WITH EMPTY EXCLUSIONS =====
    val TEST_CONFIG = Config(
        sdkVersion = SDKConstants.SDK_VERSION,
        cacheDurationHours = SDKConstants.CACHE_DURATION_HOURS,
        surveys = listOf(
            // ===== 1. BUTTON (DEFAULT) TRIGGER SURVEY =====
            SurveyConfig(
                surveyId = "button_trigger_survey",
                surveyName = "Button Trigger Survey",
                baseUrl = "https://cx.cloud4feed.com/?c=7S1X-MDAwMTIy-MDAx-kdnh",
                status = true,
                // ===== TRIGGER SETTINGS =====
                enableButtonTrigger = true,
                enableScrollTrigger = false,
                enableNavigationTrigger = false,
                enableAppLaunchTrigger = false,
                enableExitTrigger = false,
                enableTabChangeTrigger = false,

                buttonTriggerId = null,

                // ===== TRIGGER CONFIGURATION =====
                triggerScreens = emptySet(),
                triggerTabs = emptySet(),
                timeDelay = 0L,
                scrollThreshold = 0,
                triggerType = "instant",

                // ===== DISPLAY SETTINGS =====
                modalStyle = "dialog",
                animationType = "slide_up",
                backgroundColor = "#FFFFFF",

                // ===== TARGETING & LIMITS =====
                exclusionRules = emptyList(), // EMPTY EXCLUSIONS
                probability = 1.0,
                maxShowsPerSession = 999,
                cooldownPeriod = 0L,
                triggerOnce = false,
                priority = 1,

                // ===== DATA COLLECTION =====
                collectDeviceId = true,
                collectDeviceModel = false,
                collectLocation = false,
                collectAppUsage = false,

                // ===== CUSTOM PARAMETERS =====
                customParams = listOf(
                    CustomParam(
                        name = "trigger_source",
                        source = ParamSource.STORAGE,
                        key = null
                    )
                )
            ),

            // ===== 2. SCROLL AND BUTTON (CUSTOM) TRIGGER SURVEY =====
            SurveyConfig(
                surveyId = "scroll_trigger_survey",
                surveyName = "Scroll Trigger Survey",
                baseUrl = "https://cx.cloud4feed.com/?c=PTzb-MDAwMDAy-MDEx-0zMC",
                status = true,
                // ===== TRIGGER SETTINGS =====
                enableButtonTrigger = true,
                enableScrollTrigger = true,
                enableNavigationTrigger = false,
                enableAppLaunchTrigger = false,
                enableExitTrigger = false,
                enableTabChangeTrigger = false,

                buttonTriggerId = "my_custom_button",

                // ===== TRIGGER CONFIGURATION =====
                triggerScreens = setOf("notifications"),
                triggerTabs = emptySet(),
                timeDelay = 0L,
                scrollThreshold = 500,
                triggerType = "instant",

                // ===== DISPLAY SETTINGS =====
                modalStyle = "bottom_sheet",
                animationType = "slide_up",
                backgroundColor = "#F8F9FA",

                // ===== TARGETING & LIMITS =====
                exclusionRules = emptyList(), // EMPTY EXCLUSIONS
                probability = 1.0,
                maxShowsPerSession = 999,
                cooldownPeriod = 0L,
                triggerOnce = false,
                priority = 2,

                // ===== DATA COLLECTION =====
                collectDeviceId = false,
                collectDeviceModel = true,
                collectLocation = false,
                collectAppUsage = false,

                // ===== CUSTOM PARAMETERS =====
                customParams = listOf(
                    CustomParam(
                        name = "scroll_position",
                        source = ParamSource.SESSION,
                        key = "current_scroll_y"
                    )
                )
            ),

            // ===== 3. NAVIGATION TRIGGER SURVEY =====
            SurveyConfig(
                surveyId = "navigation_trigger_survey",
                surveyName = "Navigation Trigger Survey",
                baseUrl = "https://cx.cloud4feed.com/?c=uGTx-MDAwMDU1-MDA1-Nsg7",
                status = true,
                // ===== TRIGGER SETTINGS =====
                enableButtonTrigger = false,
                enableScrollTrigger = false,
                enableNavigationTrigger = true,
                enableAppLaunchTrigger = false,
                enableExitTrigger = false,
                enableTabChangeTrigger = false,

                buttonTriggerId = null,

                // ===== TRIGGER CONFIGURATION =====
                triggerScreens = setOf("notifications"),
                triggerTabs = emptySet(),
                timeDelay = 2000L,
                scrollThreshold = 0,
                triggerType = "delayed",

                // ===== DISPLAY SETTINGS =====
                modalStyle = "full_screen",
                animationType = "fade",
                backgroundColor = "#FFFFFF",

                // ===== TARGETING & LIMITS =====
                exclusionRules = emptyList(), // EMPTY EXCLUSIONS
                probability = 1.0,
                maxShowsPerSession = 9999,
                cooldownPeriod = 0L,
                triggerOnce = false,
                priority = 3,

                // ===== DATA COLLECTION =====
                collectDeviceId = true,
                collectDeviceModel = true,
                collectLocation = true,
                collectAppUsage = false,

                // ===== CUSTOM PARAMETERS =====
                customParams = listOf(
                    CustomParam(
                        name = "current_screen",
                        source = ParamSource.SESSION,
                        key = "current_screen"
                    )
                )
            ),

            // ===== 4. APP LAUNCH TRIGGER SURVEY =====
            SurveyConfig(
                surveyId = "app_launch_trigger_survey",
                surveyName = "App Launch Survey",
                baseUrl = "https://cx.cloud4feed.com/?c=WOxM-MDAwMDU1-MDAx-3k7N",
                status = true,
                // ===== TRIGGER SETTINGS =====
                enableButtonTrigger = false,
                enableScrollTrigger = false,
                enableNavigationTrigger = false,
                enableAppLaunchTrigger = true,
                enableExitTrigger = false,
                enableTabChangeTrigger = false,

                buttonTriggerId = null,

                // ===== TRIGGER CONFIGURATION =====
                triggerScreens = emptySet(),
                triggerTabs = emptySet(),
                timeDelay = 3000L,
                scrollThreshold = 0,
                triggerType = "delayed",

                // ===== DISPLAY SETTINGS =====
                modalStyle = "dialog",
                animationType = "fade",
                backgroundColor = "#FFFFFF",

                // ===== TARGETING & LIMITS =====
                exclusionRules = emptyList(), // EMPTY EXCLUSIONS
                probability = 1.0,
                maxShowsPerSession = 999,
                cooldownPeriod = 0L,
                triggerOnce = false,
                priority = 4,

                // ===== DATA COLLECTION =====
                collectDeviceId = true,
                collectDeviceModel = false,
                collectLocation = false,
                collectAppUsage = true,

                // ===== CUSTOM PARAMETERS =====
                customParams = listOf(
                    CustomParam(
                        name = "launch_count",
                        source = ParamSource.STORAGE,
                        key = "app_launch_count"
                    )
                )
            ),

            // ===== 5. EXIT TRIGGER SURVEY =====
            SurveyConfig(
                surveyId = "exit_trigger_survey",
                surveyName = "Exit Intent Survey",
                baseUrl = "https://cx.cloud4feed.com/?c=gT6V-MDAwMTQ2-MDAx-NtlH",
                status = true,
                // ===== TRIGGER SETTINGS =====
                enableButtonTrigger = false,
                enableScrollTrigger = false,
                enableNavigationTrigger = false,
                enableAppLaunchTrigger = false,
                enableExitTrigger = true,
                enableTabChangeTrigger = false,

                buttonTriggerId = null,

                // ===== TRIGGER CONFIGURATION =====
                triggerScreens = setOf("dashboard"),
                triggerTabs = emptySet(),
                timeDelay = 0L,
                scrollThreshold = 0,
                triggerType = "instant",

                // ===== DISPLAY SETTINGS =====
                modalStyle = "bottom_sheet",
                animationType = "slide_up",
                backgroundColor = "#FFEBEE",

                // ===== TARGETING & LIMITS =====
                exclusionRules = emptyList(), // EMPTY EXCLUSIONS
                probability = 1.0,
                maxShowsPerSession = 999,
                cooldownPeriod = 0L,
                triggerOnce = false,
                priority = 5,

                // ===== DATA COLLECTION =====
                collectDeviceId = false,
                collectDeviceModel = false,
                collectLocation = false,
                collectAppUsage = true,

                // ===== CUSTOM PARAMETERS =====
                customParams = listOf(
                    CustomParam(
                        name = "session_time",
                        source = ParamSource.SESSION,
                        key = "session_duration"
                    )
                )
            ),

            // ===== 6. TAB CHANGE TRIGGER SURVEY =====
            SurveyConfig(
                surveyId = "tab_change_trigger_survey",
                surveyName = "Tab Change Survey",
                baseUrl = "https://cx.cloud4feed.com/?c=1U1T-MDAwMTMw-MDAx-1YUW",
                status = true,
                // ===== TRIGGER SETTINGS =====
                enableButtonTrigger = false,
                enableScrollTrigger = false,
                enableNavigationTrigger = false,
                enableAppLaunchTrigger = false,
                enableExitTrigger = true,
                enableTabChangeTrigger = true,

                buttonTriggerId = null,
                
                // ===== TRIGGER CONFIGURATION =====
                triggerScreens = emptySet(),
                triggerTabs = setOf("mens", "womens"),
                timeDelay = 0L,
                scrollThreshold = 0,
                triggerType = "instant",

                // ===== DISPLAY SETTINGS =====
                modalStyle = "dialog",
                animationType = "slide_up",
                backgroundColor = "#FFFFFF",

                // ===== TARGETING & LIMITS =====
                exclusionRules = emptyList(), // EMPTY EXCLUSIONS
                probability = 1.0,
                maxShowsPerSession = 999,
                cooldownPeriod = 0L,
                triggerOnce = false,
                priority = 2,

                // ===== DATA COLLECTION =====
                collectDeviceId = true,
                collectDeviceModel = true,
                collectLocation = false,
                collectAppUsage = false,

                // ===== CUSTOM PARAMETERS =====
                customParams = listOf(
                    CustomParam(
                        name = "current_tab",
                        source = ParamSource.SESSION,
                        key = "current_tab"
                    )
                )
            )
        )
    )

    // ===== QUICK CONFIG SWITCHER =====
    fun getFallbackConfig(): Config {
        return EMPTY_CONFIG // For prod, empty config
        //return TEST_CONFIG // Comment this out when prod
    }
}

