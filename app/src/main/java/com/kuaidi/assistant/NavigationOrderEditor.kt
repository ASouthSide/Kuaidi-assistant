package com.kuaidi.assistant

import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

internal class NavigationOrderEditor(private val context: Context) {
    fun show(current: NavigationOrder, apply: (NavigationOrder) -> Unit) {
        var draft = current
        var active = NavigationArea.CARDS
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(4), dp(18), dp(8))
        }
        content.addView(TextView(context).apply {
            text = "拖动把手或点箭头调整顺序"
            setTextColor(ContextCompat.getColor(context, R.color.ui_muted))
            setPadding(0, dp(6), 0, dp(6))
        })
        val areaPicker = Spinner(context, Spinner.MODE_DROPDOWN).apply {
            contentDescription = "要调整的导航区域"
            setPopupBackgroundDrawable(ContextCompat.getDrawable(context, R.drawable.bg_dialog_frosted))
            minimumHeight = dp(48)
            adapter = ArrayAdapter(context, R.layout.spinner_item, NavigationArea.entries.map { it.title }).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        content.addView(areaPicker, LinearLayout.LayoutParams(-1, -2))

        val list = RecyclerView(context).apply {
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val orderAdapter = OrderAdapter { changed -> draft = draft.withOrder(active, changed) }
        val drag = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, 0) {
            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val directions = if (orderAdapter.horizontal) {
                    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
                } else {
                    ItemTouchHelper.UP or ItemTouchHelper.DOWN
                }
                return makeMovementFlags(directions, 0)
            }

            override fun onMove(list: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) =
                orderAdapter.move(source.bindingAdapterPosition, target.bindingAdapterPosition)

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun clearView(list: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(list, viewHolder)
                list.post { orderAdapter.refreshArrows() }
            }
        })
        orderAdapter.startDrag = drag::startDrag
        list.adapter = orderAdapter
        drag.attachToRecyclerView(list)
        list.addOnLayoutChangeListener { view, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft && orderAdapter.horizontal) {
                val width = right - left
                view.post { orderAdapter.setAvailableWidth(width) }
            }
        }
        content.addView(list, LinearLayout.LayoutParams(-1, -2))

        fun render() {
            val horizontal = active == NavigationArea.BOTTOM
            list.layoutDirection = if (horizontal) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_INHERIT
            list.layoutManager = LinearLayoutManager(context,
                if (horizontal) RecyclerView.HORIZONTAL else RecyclerView.VERTICAL, false)
            orderAdapter.show(draft[active], horizontal, list.width)
        }

        areaPicker.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val next = NavigationArea.entries[position]
                if (active == next) return
                active = next
                render()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        render()
        val scroll = ScrollView(context).apply { addView(content, ViewGroup.LayoutParams(-1, -2)) }
        val dialog = FrostedDialogBuilder(context).setTitle("编辑导航栏")
            .setView(scroll)
            .setNegativeButton("取消", null)
            .setNeutralButton("恢复默认", null)
            .setPositiveButton("保存") { _, _ -> apply(draft) }
            .show()
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            draft = NavigationOrder()
            render()
        }
    }

    private inner class OrderAdapter(private val onChange: (List<DashboardPage>) -> Unit) :
        RecyclerView.Adapter<OrderHolder>() {
        private var order = mutableListOf<DashboardPage>()
        var horizontal = false
            private set
        private var availableWidth = 0
        var startDrag: (RecyclerView.ViewHolder) -> Unit = {}

        fun show(newOrder: List<DashboardPage>, horizontal: Boolean, width: Int) {
            order = newOrder.toMutableList()
            this.horizontal = horizontal
            availableWidth = width
            notifyDataSetChanged()
        }

        fun setAvailableWidth(width: Int) {
            if (!horizontal || width == availableWidth) return
            availableWidth = width
            notifyDataSetChanged()
        }

        fun move(from: Int, to: Int): Boolean {
            if (from !in order.indices || to !in order.indices || from == to) return false
            order.add(to, order.removeAt(from))
            notifyItemMoved(from, to)
            onChange(order.toList())
            return true
        }

        fun refreshArrows() {
            notifyItemRangeChanged(0, itemCount)
        }

        override fun getItemCount() = order.size
        override fun getItemViewType(position: Int) = if (horizontal) 1 else 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderHolder {
            val isHorizontal = viewType == 1
            val row = LinearLayout(context).apply {
                orientation = if (isHorizontal) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(5), dp(2), dp(5), dp(2))
                setBackgroundResource(R.drawable.bg_filter)
            }
            val grip = iconButton(R.drawable.ic_reorder_grip)
            val label = TextView(context).apply {
                textSize = if (isHorizontal) 13f else 15f
                gravity = if (isHorizontal) Gravity.CENTER else Gravity.START
                setTextColor(ContextCompat.getColor(context, R.color.ui_text))
            }
            val earlier = iconButton(R.drawable.ic_reorder_arrow)
            val later = iconButton(R.drawable.ic_reorder_arrow)
            if (isHorizontal) {
                row.addView(grip, LinearLayout.LayoutParams(-1, dp(42)))
                row.addView(label, LinearLayout.LayoutParams(-1, -2))
                val moves = LinearLayout(context).apply { gravity = Gravity.CENTER }
                moves.addView(earlier, LinearLayout.LayoutParams(0, dp(42), 1f))
                moves.addView(later, LinearLayout.LayoutParams(0, dp(42), 1f))
                row.addView(moves, LinearLayout.LayoutParams(-1, -2))
            } else {
                row.addView(grip, LinearLayout.LayoutParams(dp(46), dp(48)))
                row.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(earlier, LinearLayout.LayoutParams(dp(44), dp(48)))
                row.addView(later, LinearLayout.LayoutParams(dp(44), dp(48)))
            }
            val holder = OrderHolder(row, label, grip, earlier, later)
            grip.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) startDrag(holder)
                false
            }
            row.setOnLongClickListener {
                startDrag(holder)
                true
            }
            earlier.setOnClickListener {
                val position = holder.bindingAdapterPosition
                if (move(position, position - 1)) refreshArrows()
            }
            later.setOnClickListener {
                val position = holder.bindingAdapterPosition
                if (move(position, position + 1)) refreshArrows()
            }
            return holder
        }

        override fun onBindViewHolder(holder: OrderHolder, position: Int) {
            val page = order[position]
            val width = if (horizontal) maxOf(dp(65), availableWidth / order.size - dp(4)) else -1
            holder.itemView.layoutParams = RecyclerView.LayoutParams(width, -2).apply {
                if (horizontal) setMargins(dp(2), 0, dp(2), 0) else bottomMargin = dp(5)
            }
            holder.label.text = if (horizontal) page.shortTitle else page.title
            holder.grip.contentDescription = "拖动 ${page.title}"
            holder.earlier.rotation = if (horizontal) -90f else 0f
            holder.later.rotation = if (horizontal) 90f else 180f
            holder.earlier.contentDescription = "${page.title} " + if (horizontal) "左移" else "上移"
            holder.later.contentDescription = "${page.title} " + if (horizontal) "右移" else "下移"
            holder.earlier.isEnabled = position > 0
            holder.later.isEnabled = position < order.lastIndex
            holder.earlier.alpha = if (holder.earlier.isEnabled) 1f else 0.26f
            holder.later.alpha = if (holder.later.isEnabled) 1f else 0.26f
        }
    }

    private class OrderHolder(
        row: View,
        val label: TextView,
        val grip: ImageButton,
        val earlier: ImageButton,
        val later: ImageButton
    ) : RecyclerView.ViewHolder(row)

    private fun iconButton(drawable: Int) = ImageButton(context).apply {
        setImageResource(drawable)
        imageTintList = ContextCompat.getColorStateList(context, R.color.ui_accent)
        val themeBackground = android.util.TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, themeBackground, true)) {
            setBackgroundResource(themeBackground.resourceId)
        }
        scaleType = android.widget.ImageView.ScaleType.CENTER
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()
}

