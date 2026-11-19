package com.kuaidi.assistant

import android.content.Context
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

internal class RulePage(private val context: Context) {
    private val page = DashboardScrollPage(context)
    val view = page.view

    init {
        render()
    }

    private fun render() = with(page.ui) {
        page.content.removeAllViews()
        heading("识别规则")
        text("粘贴一条脱敏样例，预览取件码和快递公司。保存时只保留规则字段。")
        button("从短信样例添加") { editLabel(null, withSample = true) }
        button("添加取件码标签") { editLabel(null, withSample = false) }
        button("添加快递公司别名") { editCarrier(null) }

        val saved = SmsRules.load(context)
        if (saved.labels.isNotEmpty()) {
            heading("我的取件码标签")
            saved.labels.forEach { label -> button(label) { editLabel(label, withSample = false) } }
        }
        if (saved.carriers.isNotEmpty()) {
            heading("我的快递公司别名")
            saved.carriers.forEach { (alias, carrier) ->
                button("$alias → $carrier") { editCarrier(alias) }
            }
        }
    }

    private fun editLabel(existing: String?, withSample: Boolean) {
        val field = input("取件码前的文字，如自提口令", existing.orEmpty())
        val content = dialogContent()
        val ui = PageControls(context, content)
        val sample = input("粘贴脱敏短信，例如：自提口令：73-5-4206").apply {
            minLines = 3
        }
        val preview = android.widget.TextView(context)
        if (withSample) {
            ui.add(sample)
            ui.button("识别并预览") {
                val body = sample.text.toString().trim()
                val label = field.text.toString().trim().ifEmpty { SmsRules.suggestLabel(body).orEmpty() }
                if (label.isNotEmpty()) field.setText(label)
                val current = SmsRules.load(context)
                val labels = if (SmsRules.validWord(label)) listOf(label) + current.labels else current.labels
                val result = SmsParser.parse(body, current.copy(labels = labels.distinct()).parserRules())
                preview.text = if (result == null) "未识别到取件码，可填写标签后重试。" else
                    listOf("取件码 ${result.pickupCode}", result.location, result.address, result.carrier)
                        .filter(String::isNotBlank).joinToString(" · ")
            }
            ui.add(preview)
        }
        ui.add(field)
        val builder = FrostedDialogBuilder(context).setTitle(if (withSample) "从短信样例添加" else "取件码标签")
            .setView(content).setPositiveButton("保存", null).setNegativeButton("取消", null)
        if (existing != null) builder.setNeutralButton("删除") { _, _ ->
            val current = SmsRules.load(context)
            SmsRules.save(context, current.copy(labels = current.labels - existing))
            render()
        }
        val dialog = builder.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val label = field.text.toString().trim()
            val current = SmsRules.load(context)
            val labels = (current.labels.filterNot { it == existing } + label).distinct()
            if (!SmsRules.validWord(label) || labels.size > SmsRules.LIMIT) {
                field.error = "请输入 1–32 个字符，最多保存 64 条规则"
            } else {
                SmsRules.save(context, current.copy(labels = labels))
                dialog.dismiss()
                render()
            }
        }
    }

    private fun editCarrier(existing: String?) {
        val alias = input("短信里的名称", existing.orEmpty())
        val carrier = input("取件列表显示的快递公司", SmsRules.load(context).carriers[existing].orEmpty())
        val content = dialogContent().apply { addView(alias); addView(carrier) }
        val builder = FrostedDialogBuilder(context).setTitle("快递公司别名").setView(content)
            .setPositiveButton("保存", null).setNegativeButton("取消", null)
        if (existing != null) builder.setNeutralButton("删除") { _, _ ->
            val current = SmsRules.load(context)
            SmsRules.save(context, current.copy(carriers = current.carriers - existing))
            render()
        }
        val dialog = builder.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val from = alias.text.toString().trim()
            val to = carrier.text.toString().trim()
            val current = SmsRules.load(context)
            val values = current.carriers - existing.orEmpty() + (from to to)
            when {
                !SmsRules.validWord(from) -> alias.error = "请输入 1–32 个字符"
                !SmsRules.validWord(to) -> carrier.error = "请输入 1–32 个字符"
                values.size > SmsRules.LIMIT -> Toast.makeText(context, "最多保存 64 条别名", Toast.LENGTH_SHORT).show()
                else -> {
                    SmsRules.save(context, current.copy(carriers = values))
                    dialog.dismiss()
                    render()
                }
            }
        }
    }

    private fun input(label: String, value: String = "") = EditText(context).apply {
        hint = label
        setText(value)
    }

    private fun dialogContent() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val padding = (20 * resources.displayMetrics.density).toInt()
        setPadding(padding, 0, padding, 0)
    }
}

