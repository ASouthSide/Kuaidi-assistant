package com.kuaidi.assistant

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class PackageAdapter(
    private val onToggleGroup: (String) -> Unit,
    private val onTogglePicked: (Package) -> Unit,
    private val onDelete: (Package) -> Unit
) : ListAdapter<PackageRow, RecyclerView.ViewHolder>(RowChanges) {

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
        val btnCollapse: TextView = v.findViewById(R.id.btn_collapse_single)
    }

    class GroupHolder(view: View) : RecyclerView.ViewHolder(view) {
        val location: TextView = view.findViewById(R.id.group_location)
        val summary: TextView = view.findViewById(R.id.group_summary)
        val toggle: TextView = view.findViewById(R.id.group_toggle)
    }

    override fun getItemViewType(position: Int) =
        if (getItem(position) is PackageRow.Group) R.layout.item_package_group else R.layout.item_package

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return if (viewType == R.layout.item_package_group) GroupHolder(view) else VH(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is PackageRow.Group -> bindGroup(holder as GroupHolder, row)
            is PackageRow.Parcel -> bindParcel(holder as VH, row)
        }
    }

    private fun bindGroup(holder: GroupHolder, row: PackageRow.Group) {
        val title = row.title.ifBlank { row.address.ifBlank { "取件点未知" } }
        val summary = if (row.location.isBlank() || row.address.isBlank()) {
            "${row.count} 件待取"
        } else {
            "${row.count} 件待取 · ${row.address}"
        }
        val action = if (row.expanded) "收起" else "展开"
        holder.location.text = title
        holder.summary.text = summary
        holder.toggle.text = action
        holder.itemView.contentDescription = "$title，$summary，$action"
        holder.itemView.setOnClickListener { onToggleGroup(row.key) }
    }

    private object RowChanges : DiffUtil.ItemCallback<PackageRow>() {
        override fun areItemsTheSame(old: PackageRow, new: PackageRow): Boolean = when {
            old is PackageRow.Group && new is PackageRow.Group -> old.key == new.key
            old is PackageRow.Parcel && new is PackageRow.Parcel -> old.value.id == new.value.id
            else -> false
        }

        override fun areContentsTheSame(old: PackageRow, new: PackageRow) = old == new
    }

    private fun bindParcel(h: VH, row: PackageRow.Parcel) {
        val p = row.value
        val displayLoc = row.title
        h.location.text = displayLoc.ifBlank { "取件点未知" }
        h.code.text = p.pickupCode
        h.code.contentDescription = "取件码 ${p.pickupCode}"
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
            h.status.setTextColor(ContextCompat.getColor(h.itemView.context, R.color.ui_done))
            h.btnPick.text = "恢复未取"
            h.overdue.text = p.pickedTime?.let { "取件 " + SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(it)) } ?: ""
        } else {
            h.status.text = "未取件"
            h.status.setTextColor(ContextCompat.getColor(h.itemView.context, R.color.ui_pending))
            h.btnPick.text = "标记已取"
            h.overdue.text = overdueText(p)
        }

        h.overdue.visibility = if (h.overdue.text.isEmpty()) View.GONE else View.VISIBLE
        h.btnPick.setOnClickListener { onTogglePicked(p) }
        h.btnDel.setOnClickListener { onDelete(p) }
        val collapseKey = row.collapseKey
        h.btnCollapse.visibility = if (collapseKey != null) View.VISIBLE else View.GONE
        h.btnCollapse.setOnClickListener(if (collapseKey != null) {
            View.OnClickListener { onToggleGroup(collapseKey) }
        } else null)
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
