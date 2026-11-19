package com.kuaidi.assistant

import android.content.Context
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import java.util.function.Consumer

internal class FrostedDialogBuilder(context: Context) : AlertDialog.Builder(context) {
    override fun create(): AlertDialog = super.create().also { dialog ->
        if (Build.VERSION.SDK_INT >= 31) dialog.window?.let(::configureBlur)
    }

    @androidx.annotation.RequiresApi(31)
    private fun configureBlur(window: Window) {
        val decor = window.decorView
        val manager = decor.context.getSystemService(WindowManager::class.java)
        val background = decor.context.getDrawable(R.drawable.bg_dialog_frosted)?.mutate() ?: return
        val radius = (26 * decor.resources.displayMetrics.density).toInt()
        window.setBackgroundDrawable(background)
        val listener = Consumer<Boolean> { enabled ->
            background.alpha = if (enabled) 204 else 255
            window.setBackgroundBlurRadius(if (enabled) radius else 0)
            window.setDimAmount(if (enabled) 0.24f else 0.36f)
        }
        listener.accept(manager.isCrossWindowBlurEnabled)
        decor.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                manager.addCrossWindowBlurEnabledListener(listener)
            }
            override fun onViewDetachedFromWindow(view: View) {
                manager.removeCrossWindowBlurEnabledListener(listener)
            }
        })
    }
}

