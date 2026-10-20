package com.c4f.mobileSDK

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SurveyBottomSheetFragment : BottomSheetDialogFragment() {

    private var surveyUrl: String = ""
    private var backgroundColor: String = "#FFFFFF"
    private var allowedDomain: String? = null

    companion object {
        private const val ARG_SURVEY_URL = "SURVEY_URL"
        private const val ARG_BACKGROUND_COLOR = "BACKGROUND_COLOR"
        private const val ARG_ALLOWED_DOMAIN = "ALLOWED_DOMAIN"

        fun newInstance(
            surveyUrl: String,
            backgroundColor: String = "#FFFFFF",
            allowedDomain: String? = null
        ): SurveyBottomSheetFragment {
            return SurveyBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SURVEY_URL, surveyUrl)
                    putString(ARG_BACKGROUND_COLOR, backgroundColor)
                    putString(ARG_ALLOWED_DOMAIN, allowedDomain)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            surveyUrl = it.getString(ARG_SURVEY_URL) ?: ""
            backgroundColor = it.getString(ARG_BACKGROUND_COLOR) ?: "#FFFFFF"
            allowedDomain = it.getString(ARG_ALLOWED_DOMAIN)
        }

        // Set style for bottom sheet
        setStyle(STYLE_NORMAL, com.google.android.material.R.style.Theme_Design_BottomSheetDialog)

        Log.d("SurveyBottomSheet", "BottomSheetFragment created - URL: $surveyUrl")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d("SurveyBottomSheet", "Creating bottom sheet view")

        // Create main container
        val mainContainer = FrameLayout(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Create WebView
        val webView = WebView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            id = android.R.id.content
        }

        // Create close button
        val closeButton = TextView(requireContext()).apply {
            text = "✕"
            setTextColor(Color.BLACK)
            textSize = 24f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.END
                topMargin = 20
                marginEnd = 20
            }
            setOnClickListener {
                dismiss()
            }
            setPadding(20, 20, 20, 20)
            setBackgroundColor(0xEEFFFFFF.toInt())
        }

        mainContainer.addView(webView)
        mainContainer.addView(closeButton)

        return mainContainer
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d("SurveyBottomSheet", "Bottom sheet view created")

        // Remove dim background
        dialog?.window?.setDimAmount(0.0f)

        // Make background transparent
        view.setBackgroundColor(Color.TRANSPARENT)

        // Find WebView and setup
        val webView = view.findViewById<WebView>(android.R.id.content)
        if (webView != null) {
            setupWebView(webView, surveyUrl, allowedDomain)

            try {
                webView.setBackgroundColor(Color.parseColor(backgroundColor))
            } catch (e: Exception) {
                webView.setBackgroundColor(Color.WHITE)
            }
        }

        // Bring close button to front
        val closeButton = view.findViewById<TextView>(android.R.id.custom)
        closeButton?.bringToFront()

        Log.d("SurveyBottomSheet", "✅ Bottom sheet setup complete")
    }

    override fun onStart() {
        super.onStart()
        Log.d("SurveyBottomSheet", "Bottom sheet onStart")

        // Ensure no dimming (keep your existing transparency logic)
        dialog?.window?.setDimAmount(0.0f)

        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        if (bottomSheet != null) {
            bottomSheet.setBackgroundColor(Color.TRANSPARENT)

            val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet)
            
            // 🛑 Disable "Swipe Down to Close"
            behavior.isHideable = false 
            
            // 🛑 Disable dragging entirely (optional, keeps it fixed)
            behavior.isDraggable = false 
            
            behavior.isFitToContents = false
            behavior.halfExpandedRatio = 0.7f
            behavior.peekHeight = 800
        }
    }

    private fun setupWebView(webView: WebView, url: String, allowedDomain: String?) {
        Log.d("SurveyBottomSheet", "Setting up WebView with URL: $url")
        try {
            WebViewConfigurator.setupSecureWebView(
                webView = webView,
                url = url,
                allowedDomain = allowedDomain
            )
            Log.d("SurveyBottomSheet", "✅ WebView configured successfully")
        } catch (e: Exception) {
            Log.e("SurveyBottomSheet", "❌ WebView setup failed: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        Log.d("SurveyBottomSheet", "Bottom sheet dismissed")

        // Notify SDK that survey completed
        activity?.let {
            com.c4f.mobileSDK.MobileSDK.getInstance().surveyCompleted()
        }
    }

    override fun onDestroy() {
    super.onDestroy()
    Log.d("SurveyBottomSheet", "🔄 Calling surveyCompleted()")
    com.c4f.mobileSDK.MobileSDK.getInstance().surveyCompleted()
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

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as com.google.android.material.bottomsheet.BottomSheetDialog
        
        // 🛑 Prevent closing when clicking outside the sheet
        dialog.setCanceledOnTouchOutside(false)
        
        // 🛑 Optional: Prevent closing when pressing the Android "Back" button
        // Uncomment the next line if you want strict "Only X button" closing
        // dialog.setCancelable(false) 

        return dialog
    }

    // Helper method to show the bottom sheet
    fun show(fragmentManager: androidx.fragment.app.FragmentManager) {
        try {
            if (!isAdded && fragmentManager.findFragmentByTag("SurveyBottomSheetFragment") == null) {
                show(fragmentManager, "SurveyBottomSheetFragment")
                Log.d("SurveyBottomSheet", "✅ Bottom sheet show() called")
            } else {
                Log.w("SurveyBottomSheet", "⚠️ Bottom sheet already shown or fragment already added")
            }
        } catch (e: Exception) {
            Log.e("SurveyBottomSheet", "❌ Failed to show bottom sheet: ${e.message}")
            e.printStackTrace()
        }
    }
}