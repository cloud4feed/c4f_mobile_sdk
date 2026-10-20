package com.c4f.mobileSDK

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager

class SurveyDialogFragment : DialogFragment() {
    private var surveyUrl: String = ""
    private var backgroundColor: String = "#FFFFFF"
    private var animationType: String = "fade"
    private var allowedDomain: String? = null

    companion object {
        private const val ARG_SURVEY_URL = "SURVEY_URL"
        private const val ARG_BACKGROUND_COLOR = "BACKGROUND_COLOR"
        private const val ARG_ANIMATION_TYPE = "ANIMATION_TYPE"
        private const val ARG_ALLOWED_DOMAIN = "ALLOWED_DOMAIN"

        fun newInstance(
            surveyUrl: String,
            backgroundColor: String = "#FFFFFF",
            animationType: String = "fade",
            allowedDomain: String? = null,
        ): SurveyDialogFragment =
            SurveyDialogFragment().apply {
                arguments =
                    Bundle().apply {
                        putString(ARG_SURVEY_URL, surveyUrl)
                        putString(ARG_BACKGROUND_COLOR, backgroundColor)
                        putString(ARG_ANIMATION_TYPE, animationType)
                        putString(ARG_ALLOWED_DOMAIN, allowedDomain)
                    }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("SurveyDialog", "=== DialogFragment onCreate ===")

        // Get arguments
        arguments?.let {
            surveyUrl = it.getString(ARG_SURVEY_URL) ?: ""
            backgroundColor = it.getString(ARG_BACKGROUND_COLOR) ?: "#FFFFFF"
            animationType = it.getString(ARG_ANIMATION_TYPE) ?: "fade"
            allowedDomain = it.getString(ARG_ALLOWED_DOMAIN)

            Log.d("SurveyDialog", "📦 Survey URL: $surveyUrl")
            Log.d("SurveyDialog", "📦 Background: $backgroundColor")
            Log.d("SurveyDialog", "📦 Animation: $animationType")
            Log.d("SurveyDialog", "📦 Domain: $allowedDomain")
        }

        if (surveyUrl.isEmpty()) {
            Log.e("SurveyDialog", "❌ Survey URL is empty!")
        }

        // Set style to remove default dialog styling
        setStyle(DialogFragment.STYLE_NORMAL, androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        Log.d("SurveyDialog", "=== Creating transparent dialog fragment ===")

        // Create a completely transparent dialog
        val dialog = Dialog(requireContext(), androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        // IMPORTANT: Make dialog completely transparent
        dialog.window?.apply {
            // Remove all backgrounds and dimming
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.0f)

            // Remove dim behind flag
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

            // Make it non-modal so background is interactive
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)

            // Set transparent background
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)

        try {
            // Create container
            val container =
                FrameLayout(requireContext()).apply {
                    layoutParams =
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        )
                    setBackgroundColor(Color.TRANSPARENT) // Transparent container
                }

            // Create WebView
            val webView =
                WebView(requireContext()).apply {
                    layoutParams =
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        )
                }

            // Create close button
            val closeButton =
                TextView(requireContext()).apply {
                    text = "✕"
                    setTextColor(Color.BLACK)
                    textSize = 24f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    layoutParams =
                        FrameLayout
                            .LayoutParams(
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                            ).apply {
                                gravity = Gravity.TOP or Gravity.END
                                topMargin = 20
                                marginEnd = 20
                            }
                    setOnClickListener {
                        Log.d("SurveyDialog", "Close button clicked")
                        dismiss()
                    }
                    setPadding(20, 20, 20, 20)
                    setBackgroundColor(0xEEFFFFFF.toInt()) // Semi-transparent white background
                }

            container.addView(webView)
            container.addView(closeButton)

            dialog.setContentView(container)

            // Set dialog size (90% of screen)
            val displayMetrics = resources.displayMetrics
            val width = (displayMetrics.widthPixels * 0.9).toInt()
            val height = (displayMetrics.heightPixels * 0.8).toInt()

            dialog.window?.setLayout(width, height)

            // Ensure transparency after layout
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            // Setup WebView BEFORE showing
            Log.d("SurveyDialog", "Setting up WebView with URL: $surveyUrl")
            setupWebView(webView, surveyUrl, allowedDomain)

            // Set background color for webview only
            try {
                webView.setBackgroundColor(Color.parseColor(backgroundColor))
            } catch (e: Exception) {
                Log.e("SurveyDialog", "Failed to parse background color: $backgroundColor")
                webView.setBackgroundColor(Color.WHITE)
            }

            // Bring close button to front
            closeButton.bringToFront()

            Log.d("SurveyDialog", "✅ Transparent dialog fragment created successfully")
        } catch (e: Exception) {
            Log.e("SurveyDialog", "❌ Error creating dialog: ${e.message}")
            e.printStackTrace()
        }

        return dialog
    }

    // In SurveyDialogFragment.kt, add this helper method
    fun showFromFlutter(fragmentManager: FragmentManager) {
        try {
            if (!isAdded && fragmentManager.findFragmentByTag("SurveyDialogFragment") == null) {
                // For Flutter, use show() with commitNow to ensure immediate display
                show(fragmentManager, "SurveyDialogFragment")
                fragmentManager.executePendingTransactions()
                Log.d("SurveyDialog", "✅ Dialog shown from Flutter")
            } else {
                Log.w("SurveyDialog", "⚠️ Dialog already shown or fragment already added")
            }
        } catch (e: Exception) {
            Log.e("SurveyDialog", "❌ Failed to show dialog from Flutter: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d("SurveyDialog", "=== Dialog onStart ===")
    }

    override fun onResume() {
        super.onResume()
        Log.d("SurveyDialog", "=== Dialog onResume ===")
    }

    override fun onPause() {
        super.onPause()
        Log.d("SurveyDialog", "=== Dialog onPause ===")
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        Log.d("SurveyDialog", "=== Dialog dismissed ===")

        // Notify SDK that survey completed
        activity?.let {
            com.c4f.mobileSDK.MobileSDK
                .getInstance()
                .surveyCompleted()
        }
    }

    private fun setupWebView(
        webView: WebView,
        url: String,
        allowedDomain: String?,
    ) {
        Log.d("SurveyDialog", "Setting up WebView")
        try {
            WebViewConfigurator.setupSecureWebView(
                webView = webView,
                url = url,
                allowedDomain = allowedDomain,
            )
            Log.d("SurveyDialog", "✅ WebView configured successfully")
        } catch (e: Exception) {
            Log.e("SurveyDialog", "❌ WebView setup failed: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("SurveyDialog", "🔄 Calling surveyCompleted()")
        com.c4f.mobileSDK.MobileSDK
            .getInstance()
            .surveyCompleted()
    }

    override fun onDestroyView() {
        // Simple WebView cleanup
        val webView = view?.findViewById<WebView>(android.R.id.content)
        webView?.let {
            try {
                it.stopLoading()
                it.clearCache(true)
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }

        super.onDestroyView()
    }

    // Helper method to show the dialog
    fun show(fragmentManager: FragmentManager) {
        try {
            if (!isAdded && fragmentManager.findFragmentByTag("SurveyDialogFragment") == null) {
                show(fragmentManager, "SurveyDialogFragment")
                Log.d("SurveyDialog", "✅ Dialog show() called")
            } else {
                Log.w("SurveyDialog", "⚠️ Dialog already shown or fragment already added")
            }
        } catch (e: Exception) {
            Log.e("SurveyDialog", "❌ Failed to show dialog: ${e.message}")
            e.printStackTrace()
        }
    }
}
