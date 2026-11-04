package com.example.chordsezy

import android.content.Context
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
fun Context.dp(v: Float): Float = v * resources.displayMetrics.density

/** Keeps content clear of the status bar and navigation bar (edge-to-edge). */
fun applySystemBarPadding(root: View) {
    val l = root.paddingLeft
    val t = root.paddingTop
    val r = root.paddingRight
    val b = root.paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.setPadding(l + bars.left, t + bars.top, r + bars.right, b + bars.bottom)
        insets
    }
}
