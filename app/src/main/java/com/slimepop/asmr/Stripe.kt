package com.slimepop.asmr

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar

object Stripe {
    fun show(activity: Activity, message: String, duration: Int = Snackbar.LENGTH_SHORT) {
        val root = activity.findViewById<View>(android.R.id.content) ?: return
        val snackbar = Snackbar.make(root, message, duration)
        val view = snackbar.view
        view.setBackgroundResource(R.drawable.stripe_bg)

        val params = view.layoutParams
        if (params is FrameLayout.LayoutParams) {
            val density = view.resources.displayMetrics.density
            params.gravity = Gravity.TOP
            val margin = (12 * density).toInt()
            params.setMargins(margin, margin, margin, margin)
            view.layoutParams = params
        }

        val text = view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        text.setTextColor(Color.WHITE)
        text.maxLines = 3
        snackbar.show()
    }
}
