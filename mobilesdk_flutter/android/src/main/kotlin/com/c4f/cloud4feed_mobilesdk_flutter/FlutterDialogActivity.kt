package com.c4f.cloud4feed_mobilesdk_flutter

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.c4f.mobileSDK.SurveyDialogFragment

class FlutterDialogActivity : FragmentActivity() {
    
    companion object {
        const val EXTRA_SURVEY_URL = "survey_url"
        const val EXTRA_BACKGROUND_COLOR = "background_color"
        const val EXTRA_ANIMATION_TYPE = "animation_type"
        const val EXTRA_ALLOWED_DOMAIN = "allowed_domain"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("FlutterDialogActivity", "📱 Creating dialog wrapper for Flutter")
        
        val surveyUrl = intent.getStringExtra(EXTRA_SURVEY_URL) ?: return
        val backgroundColor = intent.getStringExtra(EXTRA_BACKGROUND_COLOR) ?: "#FFFFFF"
        val animationType = intent.getStringExtra(EXTRA_ANIMATION_TYPE) ?: "fade"
        val allowedDomain = intent.getStringExtra(EXTRA_ALLOWED_DOMAIN)
        
        try {
            // Use SurveyDialogFragment from your existing SDK
            val dialogFragment = SurveyDialogFragment.newInstance(
                surveyUrl = surveyUrl,
                backgroundColor = backgroundColor,
                animationType = animationType,
                allowedDomain = allowedDomain
            )
            
            // Fix: Use the correct show method
            if (supportFragmentManager.findFragmentByTag("FlutterDialogFragment") == null) {
                dialogFragment.show(supportFragmentManager, "FlutterDialogFragment")
            }
            
            Log.d("FlutterDialogActivity", "✅ Dialog shown for Flutter")
        } catch (e: Exception) {
            Log.e("FlutterDialogActivity", "❌ Error: ${e.message}")
            finish()
        }
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
        try {
            com.c4f.mobileSDK.MobileSDK.getInstance().surveyCompleted()
        } catch (e: Exception) {
            Log.e("FlutterDialogActivity", "Error notifying completion: ${e.message}")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            com.c4f.mobileSDK.MobileSDK.getInstance().surveyCompleted()
        } catch (e: Exception) {
            Log.e("FlutterDialogActivity", "Error in onDestroy: ${e.message}")
        }
    }
}