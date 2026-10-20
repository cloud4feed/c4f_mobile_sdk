package com.c4f.cloud4feed_mobilesdk_flutter

import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import android.app.Activity
import android.content.Context
import android.util.Log
import com.c4f.mobileSDK.MobileSDK // Core SDK Import

class MobileSdkFlutterPlugin: FlutterPlugin, MethodCallHandler, ActivityAware {
    private lateinit var channel : MethodChannel
    private var activity: Activity? = null

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "cloud4feed_mobilesdk_flutter")
        channel.setMethodCallHandler(this)
        Log.d("MobileSDKFlutter", "🔌 Plugin attached via MethodChannel")
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        
        when (call.method) {
            // =====================================================================
            // 🚀 1. INITIALIZATION & SETUP
            // =====================================================================
            "initialize" -> {
                val apiKey = call.argument<String>("apiKey")
                if (apiKey != null && activity != null) {
                    try {
                        val context = activity!!.applicationContext
                        
                        // ALWAYS use simple initialize (no parameters) first
                        MobileSDK.initialize(context, apiKey)
                        
                        // If we have parameters, set them via reflection (same as RN)
                        val params = call.argument<List<Any>>("params")
                        if (params != null && params.isNotEmpty()) {
                            setParametersFromFlutter(context, params)
                        }
                        
                        Log.d("MobileSDKFlutter", "✅ SDK initialized with ${params?.size ?: 0} parameters")
                        result.success(true)
                        
                    } catch (e: Exception) {
                        Log.e("MobileSDKFlutter", "❌ Init failed", e)
                        result.error("INIT_ERROR", e.message, null)
                    }
                } else {
                    result.error("INVALID_ARGS", "API key or activity missing", null)
                }
            }
            
            "autoSetup" -> {
                if (activity != null) {
                    try {
                        // Flutter için sadece Lifecycle (App Launch) takibi yapar.
                        // Buton taraması Flutter tarafında Widget ile yapılır.
                        MobileSDK.getInstance().autoSetup(activity!!)
                        Log.d("MobileSDKFlutter", "✅ Lifecycle Auto-Setup completed")
                        result.success(true)
                    } catch (e: Exception) {
                        result.error("SETUP_ERROR", e.message, null)
                    }
                } else {
                    result.error("NO_ACTIVITY", "No activity", null)
                }
            }

            // =====================================================================
            // 🔗 2. TRIGGERS (Flutter'dan Gelen Sinyaller)
            // =====================================================================
            
            "triggerButton" -> {
                val buttonId = call.argument<String>("buttonId")
                if (activity != null && buttonId != null) {
                    // Core SDK'daki String ID eşleştiriciyi kullanır
                    MobileSDK.getInstance().triggerButtonByStringId(buttonId, activity!!)
                    result.success(true)
                } else {
                    result.success(false)
                }
            }

            "triggerNavigation" -> {
                val screenName = call.argument<String>("screenName")
                if (activity != null && screenName != null) {
                    // Core SDK'ya navigasyon bilgisi ver
                    MobileSDK.getInstance().triggerByNavigation(screenName, activity!!)
                    // Tab değişimi de olabilir, onu da kontrol et
                    MobileSDK.getInstance().triggerByTabChange(screenName, activity!!)
                    result.success(true)
                } else {
                    result.success(false)
                }
            }

            "triggerTabChange" -> {
                val tabName = call.argument<String>("tabName")
                if (activity != null && tabName != null) {
                    MobileSDK.getInstance().triggerByTabChange(tabName, activity!!)
                    result.success(true)
                } else {
                    result.success(false)
                }
            }

            "triggerScroll" -> {
                if (activity != null) {
                    val scrollY = call.argument<Int>("scrollY") ?: 500
                    MobileSDK.getInstance().triggerScrollManual(activity!!, scrollY)
                    result.success(true)
                } else {
                    result.success(false)
                }
            }

            "enableNavigationSafety" -> {
                try {
                    MobileSDK.getInstance().enableNavigationSafety()
                    result.success(true)
                } catch (e: Exception) {
                    result.error("SAFETY_ERROR", e.message, null)
                }
            }

            "autoSetupSafe" -> {
                if (activity != null) {
                    try {
                        MobileSDK.getInstance().autoSetupSafe(activity!!)
                        result.success(true)
                    } catch (e: Exception) {
                        result.error("SETUP_ERROR", e.message, null)
                    }
                } else {
                    result.error("NO_ACTIVITY", "No activity", null)
                }
            }

            "getCurrentParameters" -> {
                try {
                    val params = MobileSDK.getCurrentParameters()
                    // Convert Map to JSON string for Flutter
                    val json = params.entries.joinToString(", ", "{", "}") { 
                        "\"${it.key}\":\"${it.value}\"" 
                    }
                    result.success(json)
                } catch (e: Exception) {
                    result.error("PARAMS_ERROR", e.message, null)
                }
            }

            // =====================================================================
            // 🛠️ 3. SHOW & DISPLAY METHODS
            // =====================================================================

            "showSurvey" -> {
                if (activity != null) {
                    MobileSDK.getInstance().showSurvey(activity!!)
                    result.success(true)
                } else {
                    result.error("NO_ACTIVITY", "No activity", null)
                }
            }
            
            "showSurveyById" -> {
                val surveyId = call.argument<String>("surveyId")
                if (activity != null && surveyId != null) {
                    // DÜZELTME: Manuel Intent açmak yerine Core SDK'ya devrettik.
                    // Core SDK config'e bakıp Dialog mu BottomSheet mi karar verecek.
                    MobileSDK.getInstance().showSurveyById(activity!!, surveyId)
                    result.success(true)
                } else {
                    result.error("INVALID_ARGS", "Missing ID", null)
                }
            }

            // =====================================================================
            // ⚙️ 4. USER PROPERTIES & SESSION
            // =====================================================================

            "setUserProperty" -> {
                val key = call.argument<String>("key")
                val value = call.argument<String>("value")
                if (key != null && value != null && activity != null) {
                    // Veriyi SharedPreferences'a yazıyoruz, Core SDK buradan okuyor
                    activity!!.getSharedPreferences("survey_sdk_data", Context.MODE_PRIVATE)
                        .edit().putString(key, value).apply()
                    result.success(true)
                } else {
                    result.success(false)
                }
            }

            "setSessionData" -> {
                val key = call.argument<String>("key")
                val value = call.argument<String>("value")
                if (key != null && value != null) {
                    try {
                        MobileSDK.getInstance().setSessionData(key, value)
                        result.success(true)
                    } catch (e: Exception) {
                        result.error("SESSION_ERROR", e.message, null)
                    }
                } else {
                    result.error("INVALID_ARGS", "Args missing", null)
                }
            }

            "resetSessionData" -> {
                try {
                    MobileSDK.getInstance().resetSessionData()
                    result.success(true)
                } catch (e: Exception) {
                    result.error("SESSION_ERROR", e.message, null)
                }
            }

            "trackEvent" -> {
                // Core SDK'da trackEvent public değilse veya otomatikse burası loglama yapar
                val eventName = call.argument<String>("eventName")
                val properties = call.argument<Map<String, Any>>("properties")
                Log.d("MobileSDKFlutter", "Track Event: $eventName, Props: $properties")
                // Eğer Core SDK'da public bir trackEvent varsa:
                // MobileSDK.getInstance().trackEvent(eventName, properties)
                result.success(true)
            }

            // =====================================================================
            // 📊 5. STATUS & DEBUGGING
            // =====================================================================
            
            "getDebugStatus" -> {
                try {
                    val status = MobileSDK.getInstance().debugSurveyStatus()
                    result.success(status)
                } catch (e: Exception) {
                    result.success("Error: ${e.message}")
                }
            }

            "getSurveyIds" -> {
                try {
                    val ids = MobileSDK.getInstance().getSurveyIds()
                    result.success(ids)
                } catch (e: Exception) {
                    result.error("ERROR", e.message, null)
                }
            }

            "isSDKEnabled" -> {
                try {
                    result.success(MobileSDK.getInstance().isSDKEnabled())
                } catch (e: Exception) {
                    result.error("ERROR", e.message, null)
                }
            }

            "isConfigurationLoaded" -> {
                try {
                    result.success(MobileSDK.getInstance().isConfigurationLoaded())
                } catch (e: Exception) {
                    result.error("ERROR", e.message, null)
                }
            }
            
            "getQueueStatus" -> {
                try {
                    result.success(MobileSDK.getInstance().getQueueStatus())
                } catch (e: Exception) {
                    result.error("ERROR", e.message, null)
                }
            }
            
            "isShowingSurvey" -> {
                try {
                    result.success(MobileSDK.getInstance().isShowingSurvey())
                } catch (e: Exception) {
                    result.error("ERROR", e.message, null)
                }
            }

            // =====================================================================
            // 🧹 6. CLEANUP & EXCLUSIONS
            // =====================================================================

            "isUserExcluded" -> {
                try {
                    result.success(MobileSDK.getInstance().isUserExcluded())
                } catch (e: Exception) {
                    result.error("ERROR", e.message, null)
                }
            }

            "isUserExcludedForSurvey" -> {
                val surveyId = call.argument<String>("surveyId")
                if (surveyId != null) {
                    try {
                        result.success(MobileSDK.getInstance().isUserExcluded(surveyId))
                    } catch (e: Exception) {
                        result.error("ERROR", e.message, null)
                    }
                } else {
                    result.error("INVALID_ARGS", "Missing ID", null)
                }
            }

            "clearSurveyQueue" -> {
                try {
                    MobileSDK.getInstance().clearSurveyQueue()
                    result.success(true)
                } catch (e: Exception) {
                    result.error("ERROR", e.message, null)
                }
            }

            "resetTriggers" -> {
                try {
                    MobileSDK.getInstance().resetTriggers()
                    result.success(true)
                } catch (e: Exception) {
                    result.error("ERROR", e.message, null)
                }
            }
            
            "fetchConfiguration" -> {
                try {
                    MobileSDK.getInstance().fetchConfiguration()
                    result.success(true)
                } catch (e: Exception) {
                    result.error("ERROR", e.message, null)
                }
            }
            
            "getConfigForDebug" -> {
                try {
                    result.success(MobileSDK.getInstance().getConfigForDebug())
                } catch (e: Exception) {
                    result.error("ERROR", e.message, null)
                }
            }

            "cleanup" -> {
                try { 
                    MobileSDK.getInstance().cleanup()
                    result.success(true) 
                } catch (e: Exception) { 
                    result.success(false) 
                }
            }
            
            else -> result.notImplemented()
        }
    }

    private fun setParametersFromFlutter(context: Context, params: List<Any>) {
        try {
            Log.d("MobileSDKFlutter", "📦 Setting ${params.size} parameters from Flutter")
            
            // Get the SDK instance
            val mobileSDK = MobileSDK.getInstance()
            
            // Use reflection to access the customParams field
            val customParamsField = mobileSDK.javaClass.getDeclaredField("customParams")
            customParamsField.isAccessible = true
            
            // Get current parameters
            val currentParams = (customParamsField.get(mobileSDK) as? Map<*, *>)?.let {
                try {
                    @Suppress("UNCHECKED_CAST")
                    it as MutableMap<String, String>
                } catch (e: Exception) {
                    mutableMapOf<String, String>()
                }
            } ?: mutableMapOf<String, String>()
            
            // Add parameters from Flutter
            params.forEach { param ->
                when (param) {
                    is String -> {
                        // Look up from storage
                        val value = try {
                            val storageUtilsClass = Class.forName("com.c4f.mobileSDK.StorageUtils")
                            val method = storageUtilsClass.getDeclaredMethod("findSpecificData", Context::class.java, String::class.java)
                            method.invoke(null, context, param) as? String
                        } catch (e: Exception) {
                            null
                        }
                        if (value != null) {
                            currentParams[param] = value
                            Log.d("MobileSDKFlutter", "   ✅ From storage: $param = $value")
                        } else {
                            Log.d("MobileSDKFlutter", "   ⚠️ '$param' not found in storage")
                        }
                    }
                    is Map<*, *> -> {
                        // Direct key-value pair
                        val map = param as Map<String, Any>
                        map.entries.forEach { entry ->
                            val value = entry.value.toString()
                            currentParams[entry.key] = value
                            Log.d("MobileSDKFlutter", "   ✅ Direct param: ${entry.key} = $value")
                        }
                    }
                    else -> {
                        Log.w("MobileSDKFlutter", "⚠️ Skipping invalid parameter type: ${param::class.java.simpleName}")
                    }
                }
            }
            
            // Update the customParams field
            customParamsField.set(mobileSDK, currentParams)
            
            Log.d("MobileSDKFlutter", "✅ Set ${currentParams.size} parameters via reflection")
            
        } catch (e: Exception) {
            Log.e("MobileSDKFlutter", "❌ Failed to set parameters", e)
        }
}

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }
}