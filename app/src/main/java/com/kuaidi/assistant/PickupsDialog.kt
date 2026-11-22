package com.kuaidi.assistant

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

internal object PickupsDialog {
    fun show(context: Context, onMark: (Package) -> Unit) {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val inset = (20 * resources.displayMetrics.density).toInt()
            setPadding(inset, 0, inset, inset)
        }
        val scroll = ScrollView(context).apply { addView(content) }
        val dialog = FrostedDialogBuilder(context).setTitle("待取快递")
            .setView(scroll)
            .setNegativeButton("关闭", null)
            .create()

        fun render() {
            content.removeAllViews()
            val pending = PendingPickups.ordered(PackageStore.load(context))
            val first = pending.firstOrNull()
            val ui = PageControls(context, content)
            if (first == null) {
                ui.text("没有待取快递")
                return
            }
            val aliases = PackageStore.loadAliases(context)
            ui.text(PendingPickups.title(pending.size) + " · 最早到达")
            ui.heading("取件码 ${first.pickupCode}")
            ui.text("地址：${PendingPickups.address(first, aliases)}")
            ui.button("标记已取") {
                onMark(first)
                render()
            }

            val remaining = pending.drop(1)
            if (remaining.isEmpty()) return
            val folded = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
            }
            val toggle = ui.button("展开其余 ${remaining.size} 件") {}
            toggle.setOnClickListener {
                val expand = folded.visibility != View.VISIBLE
                if (expand && folded.childCount == 0) {
                    val details = PageControls(context, folded)
                    remaining.forEach { pkg ->
                        details.heading("取件码 ${pkg.pickupCode}")
                        details.text("地址：${PendingPickups.address(pkg, aliases)}")
                    }
                }
                folded.visibility = if (expand) View.VISIBLE else View.GONE
                toggle.text = if (expand) "收起其余快递" else "展开其余 ${remaining.size} 件"
            }
            ui.add(folded)
        }

        render()
        dialog.show()
    }
}
