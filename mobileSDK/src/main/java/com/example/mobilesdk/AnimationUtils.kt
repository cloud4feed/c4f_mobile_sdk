package com.c4f.mobileSDK

import android.app.Activity
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.util.Log

object AnimationUtils {

    fun applyEnterTransition(activity: Activity, animationType: String) {
        val (enterAnim, exitAnim) = getAnimationPair(animationType, true)
        applyTransition(activity, enterAnim, exitAnim, isOpen = true)
    }

    fun applyExitTransition(activity: Activity, animationType: String) {
        val (enterAnim, exitAnim) = getAnimationPair(animationType, false)
        applyTransition(activity, enterAnim, exitAnim, isOpen = false)
    }

    private fun applyTransition(
        activity: Activity,
        enterAnimName: String,
        exitAnimName: String,
        isOpen: Boolean
    ) {
        val (enterAnim, exitAnim) = getAnimPair(activity, enterAnimName, exitAnimName)
        if (enterAnim == 0 && exitAnim == 0) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && activity is AppCompatActivity) {
            // The transition type must reflect whether the activity is opening or
            // closing, not the animation resource name. Deriving it from the name
            // (looking for "enter") was always false here, so the OPEN override was
            // never registered and the system played its default transition.
            val transitionType = if (isOpen) {
                AppCompatActivity.OVERRIDE_TRANSITION_OPEN
            } else {
                AppCompatActivity.OVERRIDE_TRANSITION_CLOSE
            }
            activity.overrideActivityTransition(transitionType, enterAnim, exitAnim)
        } else {
            activity.overridePendingTransition(enterAnim, exitAnim)
        }
    }

    private fun getAnimationPair(animationType: String, isEnter: Boolean): Pair<String, String> {
        return when (animationType) {
            "slide_up" -> if (isEnter) "slide_in_bottom" to "fade_out" else "fade_in" to "slide_out_bottom"
            "fade" -> "fade_in" to "fade_out"
            "slide_down" -> if (isEnter) "slide_in_top" to "fade_out" else "fade_in" to "slide_out_top"
            else -> "" to ""
        }
    }

    private fun getAnimPair(activity: Activity, enterAnimName: String, exitAnimName: String): Pair<Int, Int> {
        val enterAnim = getAnimationResource(activity, enterAnimName)
        val exitAnim = getAnimationResource(activity, exitAnimName)
        return Pair(enterAnim, exitAnim)
    }

    private fun getAnimationResource(activity: Activity, name: String): Int {
        var animRes = activity.resources.getIdentifier(name, "anim", activity.packageName)

        if (animRes == 0) {
            animRes = when (name) {
                "fade_in" -> android.R.anim.fade_in
                "fade_out" -> android.R.anim.fade_out
                "slide_in_bottom" -> android.R.anim.slide_in_left
                "slide_out_bottom" -> android.R.anim.slide_out_right
                "slide_in_top" -> android.R.anim.slide_in_left
                "slide_out_top" -> android.R.anim.slide_out_right
                else -> 0
            }

            if (animRes != 0) {
                Log.d("AnimationUtils", "Using system fallback for: $name")
            }
        }

        if (animRes == 0) {
            Log.w("AnimationUtils", "Animation resource not found: $name")
        }

        return animRes
    }
}