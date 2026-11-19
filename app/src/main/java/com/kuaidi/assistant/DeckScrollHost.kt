package com.kuaidi.assistant

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/** Lets the inner list scroll before passing vertical drags at its edges to the deck. */
class DeckScrollHost(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var handingOff = false
    private var startedAtScrollableTop = false
    private val pageSwipeDistance = 64 * resources.displayMetrics.density
    internal var onSwipeDownAtTop: (() -> Unit)? = null

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        super.requestDisallowInterceptTouchEvent(disallowIntercept && !handingOff)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val content = getChildAt(0) ?: return super.dispatchTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handingOff = false
                downX = event.x
                downY = event.y
                lastY = event.y
                val scrollable = content.canScrollVertically(-1) || content.canScrollVertically(1)
                startedAtScrollableTop = onSwipeDownAtTop != null && scrollable && !content.canScrollVertically(-1)
                if (scrollable) requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val totalY = event.y - downY
                val stepY = event.y - lastY
                lastY = event.y
                if (abs(totalY) > touchSlop && abs(totalY) > abs(event.x - downX) && stepY != 0f) {
                    val direction = if (stepY < 0) 1 else -1
                    val changingPage = startedAtScrollableTop && totalY > 0f &&
                        !content.canScrollVertically(-1)
                    if (!changingPage && !content.canScrollVertically(direction)) handingOff = true
                    requestDisallowInterceptTouchEvent(!handingOff)
                }
            }
            MotionEvent.ACTION_UP -> {
                val totalY = event.y - downY
                if (startedAtScrollableTop && !content.canScrollVertically(-1) &&
                    totalY >= pageSwipeDistance && totalY > abs(event.x - downX)) {
                    val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    requestDisallowInterceptTouchEvent(false)
                    post { onSwipeDownAtTop?.invoke() }
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }
}
