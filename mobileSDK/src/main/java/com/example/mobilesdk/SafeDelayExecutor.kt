package com.c4f.mobileSDK

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

class SafeDelayExecutor {

    companion object {
        private val handlers = ConcurrentHashMap<String, WeakReference<Handler>>()
        private val activeDelays = ConcurrentHashMap<String, Boolean>()

        fun executeDelayed(
            key: String,
            delayMillis: Long,
            action: () -> Unit
        ): Boolean {
            cancelDelayed(key)

            return try {
                // Prevent duplicate executions
                if (activeDelays[key] == true) {
                    Log.d("SafeDelayExecutor", "Delay already active for key: $key")
                    return false
                }

                activeDelays[key] = true
                val handler = Handler(Looper.getMainLooper())
                handlers[key] = WeakReference(handler)

                handler.postDelayed({
                    activeDelays.remove(key)
                    handlers.remove(key)
                    try {
                        action()
                    } catch (e: Exception) {
                        Log.e("SafeDelayExecutor", "Error in delayed action: ${e.message}")
                    }
                }, delayMillis)
                true
            } catch (e: Exception) {
                activeDelays.remove(key)
                handlers.remove(key)
                Log.e("SafeDelayExecutor", "Failed to execute delayed action: ${e.message}")
                false
            }
        }

        fun cancelDelayed(key: String) {
            handlers[key]?.get()?.removeCallbacksAndMessages(null)
            handlers.remove(key)
            activeDelays.remove(key)
        }

        fun cancelAll() {
            handlers.values.forEach { it.get()?.removeCallbacksAndMessages(null) }
            handlers.clear()
            activeDelays.clear()
        }

        fun cleanup() {
            cancelAll()
        }

        fun isDelayActive(key: String): Boolean {
            return activeDelays[key] == true
        }
    }
}

class ActivitySafeExecutor(activity: Activity) {
    private val weakActivity = WeakReference(activity)
    private val executorKey = "activity_${activity.hashCode()}_${System.currentTimeMillis()}"

    fun executeDelayed(
        delayMillis: Long,
        action: (activity: Activity?) -> Unit
    ) {
        SafeDelayExecutor.executeDelayed(executorKey, delayMillis) {
            val targetActivity = weakActivity.get()
            if (targetActivity != null && !targetActivity.isFinishing && !targetActivity.isDestroyed) {
                try {
                    action(targetActivity)
                } catch (e: Exception) {
                    Log.e("ActivitySafeExecutor", "Error in activity action: ${e.message}")
                }
            } else {
                Log.d("ActivitySafeExecutor", "Activity gone, skipping action")
            }
        }
    }

    fun cancel() {
        SafeDelayExecutor.cancelDelayed(executorKey)
    }

    fun isActive(): Boolean {
        return SafeDelayExecutor.isDelayActive(executorKey)
    }
}