package com.kuaidi.assistant

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

internal class PageControls(private val context: Context, private val content: LinearLayout) {
    fun heading(value: String) = text(value, 19f).apply {
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, px(12), 0, px(4))
        androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
    }

    fun text(value: String, size: Float = 14f) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(ContextCompat.getColor(context, R.color.ui_text))
        setPadding(0, px(4), 0, px(4))
        add(this)
    }

    fun button(label: String, action: () -> Unit) = MaterialButton(
        context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply {
        text = label
        minHeight = px(48)
        setOnClickListener { action() }
        add(this)
    }

    fun add(view: View) {
        content.addView(view, LinearLayout.LayoutParams(-1, -2))
    }

    private fun px(value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()
}

internal class DashboardScrollPage(context: Context) {
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val padding = (16 * resources.displayMetrics.density).toInt()
        setPadding(padding, 0, padding, padding)
    }
    private val scrollHost = DeckScrollHost(context, null).apply {
        id = R.id.deck_scroll_host
        addView(ScrollView(context).apply {
            addView(content)
        }, android.widget.FrameLayout.LayoutParams(-1, -1))
    }
    val view = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_section_stack)
        setPadding(0, 0, 0, (9 * resources.displayMetrics.density).toInt())
        addView(scrollHost, LinearLayout.LayoutParams(-1, 0, 1f))
    }
    val ui = PageControls(context, content)
}

