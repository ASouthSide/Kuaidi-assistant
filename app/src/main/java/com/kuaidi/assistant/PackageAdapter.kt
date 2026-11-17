package com.kuaidi.assistant

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PackageAdapter(
    var list: MutableList<Package>,
    private val nameResolver: (String) -> String,
    private val onTogglePicked: (Package) -> Unit,
    private val onDelete: (Package) -> Unit
) : RecyclerView.Adapter<PackageAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val location: TextView = v.findViewById(R.id.tv_location)
        val code: TextView = v.findViewById(R.id.tv_code)
        val address: TextView = v.findViewById(R.id.tv_address)
        val carrier: TextView = v.findViewById(R.id.tv_carrier)
        val time: TextView = v.findViewById(R.id.tv_time)
        val status: TextView = v.findViewById(R.id.tv_status)
        val overdue: TextView = v.findViewById(R.id.tv_overdue)
        val btnPick: TextView = v.findViewById(R.id.btn_pick)
        val btnDel: TextView = v.findViewById(R.id.btn_del)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_package, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val p = list[pos]
        val displayLoc = nameResolver(p.location)
        h.location.text = displayLoc.ifBlank { "取件点未知" }
        h.code.text = "取件码 ${p.pickupCode}"
        if (p.address.isNotBlank()) {
            h.address.text = "地址：${p.address}"
            h.address.visibility = View.VISIBLE
        } else {
            h.address.visibility = View.GONE
        }
        h.carrier.text = p.carrier.ifBlank { "快递" }
        h.time.text = "到达 " + SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(p.arriveTime))

        if (p.picked) {
            h.status.text = "已取件"
            h.status.setBackgroundColor(0xFF16A34A.toInt())
            h.btnPick.text = "恢复未取"
            h.overdue.text = p.pickedTime?.let { "取件 " + SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(it)) } ?: ""
        } else {
            h.status.text = "未取件"
            h.status.setBackgroundColor(0xFFEA580C.toInt())
            h.btnPick.text = "标记已取"
            h.overdue.text = overdueText(p)
        }

        h.btnPick.setOnClickListener { onTogglePicked(p) }
        h.btnDel.setOnClickListener { onDelete(p) }
    }

    private fun overdueText(p: Package): String {
        val diff = System.currentTimeMillis() - p.arriveTime
        val hours = diff / 3600000
        return when {
            hours < 1 -> "刚到，请及时取"
            hours < 24 -> "已到 ${hours} 小时"
            else -> "⚠ 已超 ${hours / 24} 天 ${hours % 24} 小时未取"
        }
    }
}
