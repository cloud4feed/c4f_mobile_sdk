package com.c4f.mobileSDK

import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class BackPressHandler(
    private val activity: AppCompatActivity,
    private val tag: String,
    private val onBackPressed: () -> Unit
) {
    private var backPressedCallback: OnBackPressedCallback? = null

    fun enable() {
        disable()

        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d(tag, "Back gesture detected")
                onBackPressed.invoke()
            }
        }

        activity.onBackPressedDispatcher.addCallback(activity, backPressedCallback!!)
        Log.d(tag, "Back press handler enabled")
    }

    fun disable() {
        backPressedCallback?.remove()
        backPressedCallback = null
        Log.d(tag, "Back press handler disabled")
    }
}