package com.kuaidi.assistant

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.material.materialswitch.MaterialSwitch

internal class PreferencesPage(
    private val activity: AppCompatActivity,
    private val visuals: AppVisuals,
    private val choosePhoto: (AppVisuals.Slot) -> Unit,
    private val goHome: () -> Unit,
    private val requestPermission: (String) -> Unit
) {
    private val page = DashboardScrollPage(activity)
    val view = page.view

    private val notificationStatus: android.widget.TextView
    private val smsStatus: android.widget.TextView?

    init {
        with(page.ui) {
            heading("设置")
            heading("首页与导航")
            button("回到首页", goHome)
            val homeTarget = button(homeLabel()) {}
            homeTarget.setOnClickListener {
                val pages = DashboardPage.entries
                FrostedDialogBuilder(activity).setTitle("选择首页目标")
                    .setSingleChoiceItems(pages.map { it.title }.toTypedArray(), Settings.homePage(activity).ordinal) { dialog, index ->
                        Settings.setHomePage(activity, pages[index])
                        homeTarget.text = homeLabel()
                        dialog.dismiss()
                    }.setNegativeButton("取消", null).show()
            }
            heading("开机自启")
            toggle("开机后恢复提醒任务", Settings.restoreOnBoot(activity)) {
                Settings.setRestoreOnBoot(activity, it)
            }
            button("查看自启动设置指引") { showBootGuide() }

            heading("个性化")
            button("选择应用背景照片") { choosePhoto(AppVisuals.Slot.BACKGROUND) }
            toggle("模糊应用背景", visuals.blur(AppVisuals.Slot.BACKGROUND)) { visuals.setBlur(AppVisuals.Slot.BACKGROUND, it) }
            button("移除应用背景") { visuals.removePhoto(AppVisuals.Slot.BACKGROUND) }
            button("选择开屏照片") { choosePhoto(AppVisuals.Slot.SPLASH) }
            toggle("模糊开屏照片", visuals.blur(AppVisuals.Slot.SPLASH)) { visuals.setBlur(AppVisuals.Slot.SPLASH, it) }
            val splashLength = button(splashLabel()) {}
            splashLength.setOnClickListener {
                val options = arrayOf("关闭", "1 秒", "2 秒", "3 秒", "4 秒", "5 秒")
                FrostedDialogBuilder(activity).setTitle("开屏时长")
                    .setSingleChoiceItems(options, visuals.seconds()) { dialog, index ->
                        visuals.setSeconds(index)
                        splashLength.text = splashLabel()
                        dialog.dismiss()
                    }.setNegativeButton("取消", null).show()
            }
            button("移除开屏照片") { visuals.removePhoto(AppVisuals.Slot.SPLASH) }

            heading("权限与超期提醒")
            notificationStatus = text("")
            button("检查或开启通知权限") { openNotificationPermission() }
            val overdue = button(overdueLabel()) {}
            overdue.setOnClickListener {
                askText("超期提醒天数", "输入 1–365 天", Settings.getOverdueDays(activity).toString(), true) { raw ->
                    val value = raw.toIntOrNull()?.takeIf { it in 1..365 } ?: return@askText false
                    Settings.setOverdueDays(activity, value)
                    overdue.text = overdueLabel()
                    OverdueScheduler.schedule(activity)
                    OverdueReceiver.checkAndNotify(activity)
                    true
                }
            }
            smsStatus = if (supportsSmsReception) {
                val status = text("")
                button("检查或开启接收短信权限") { openSmsPermission() }
                status
            } else {
                text("商店候选版可使用粘贴短信、选图和手动录入。")
                null
            }
            text("选图和拍照会按需打开系统界面。")

            heading("淘宝身份码")
            toggle("标记已取时打开淘宝身份码", Settings.getOpenIdentityOnPickup(activity)) {
                Settings.setOpenIdentityOnPickup(activity, it)
            }
            button("管理自动打开的取件点") { editLocations() }

            heading("存储")
            button("清除缓存") { clearCache() }

            heading("关于")
            val version = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
            text("快递助手 · $version")
            text("开发者：ASouthSide")
            text("开源协议：MIT")
            text("包裹、规则和个性化照片保存在应用存储中。规则样例只用于预览。侧载版接收新短信，应用不主动上传取件数据。系统备份由设备设置管理。")
            button("打开 GitHub 项目") {
                open(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ASouthSide/Kuaidi-assistant")))
            }
        }
        refreshPermissions()
    }

    private fun toggle(label: String, state: Boolean, change: (Boolean) -> Unit) {
        page.ui.add(MaterialSwitch(activity).apply {
            text = label
            minHeight = (48 * resources.displayMetrics.density).toInt()
            isChecked = state
            setOnCheckedChangeListener { _, checked -> change(checked) }
        })
    }

    fun refreshPermissions() {
        val allowed = NotificationManagerCompat.from(activity).areNotificationsEnabled()
        val channel = activity.getSystemService(NotificationManager::class.java).getNotificationChannel("kuaidi_overdue")
        notificationStatus.text = when {
            !allowed -> "通知权限：未开启"
            channel?.importance == NotificationManager.IMPORTANCE_NONE -> "超期提醒频道：已关闭"
            else -> "通知权限：已开启"
        }
        smsStatus?.text = if (hasPermission(Manifest.permission.RECEIVE_SMS)) "接收短信权限：已开启" else "接收短信权限：未开启"
    }

    private fun openNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
            && !requestedBefore(Manifest.permission.POST_NOTIFICATIONS)) {
            requestPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            open(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, activity.packageName))
        }
    }

    private fun openSmsPermission() {
        if (hasPermission(Manifest.permission.RECEIVE_SMS) || requestedBefore(Manifest.permission.RECEIVE_SMS)) appDetails()
        else requestPermission(Manifest.permission.RECEIVE_SMS)
    }

    private fun requestedBefore(permission: String): Boolean {
        val prefs = activity.getSharedPreferences("permission_requests", Context.MODE_PRIVATE)
        val result = prefs.getBoolean(permission, false)
        if (!result) prefs.edit().putBoolean(permission, true).apply()
        return result
    }

    private fun showBootGuide() {
        FrostedDialogBuilder(activity).setTitle("开机自启设置")
            .setMessage("打开应用内的恢复提醒开关后，系统允许时会在重启后恢复定时提醒。部分手机还需要从系统应用管理开启自启动，并按需允许后台运行。不同品牌的菜单名称可能不同。")
            .setPositiveButton("应用详情") { _, _ -> appDetails() }
            .setNeutralButton("电池设置") { _, _ -> open(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            .setNegativeButton("关闭", null).show()
    }

    private fun editLocations() {
        val known = PackageStore.load(activity).flatMap { listOf(it.location.trim(), it.address.trim()) }.filter(String::isNotBlank)
        val aliases = PackageStore.loadAliases(activity)
        val stored = Settings.identityLocations(activity)
        val options = (known + aliases.keys + stored.orEmpty()).distinct().sorted()
        val labels = listOf("所有取件点") + options.map { location ->
            aliases[location]?.let { "$it（$location）" } ?: location
        }
        val checked = BooleanArray(labels.size) { index ->
            if (index == 0) stored == null else stored?.contains(options[index - 1]) == true
        }
        FrostedDialogBuilder(activity).setTitle("自动打开身份码的取件点")
            .setMultiChoiceItems(labels.toTypedArray(), checked) { dialog, index, selected ->
                checked[index] = selected
                val list = (dialog as AlertDialog).listView
                if (index == 0 && selected) {
                    for (item in 1 until checked.size) {
                        checked[item] = false
                        list.setItemChecked(item, false)
                    }
                } else if (index > 0 && selected) {
                    checked[0] = false
                    list.setItemChecked(0, false)
                }
            }
            .setPositiveButton("保存") { _, _ ->
                Settings.setIdentityLocations(activity, if (checked[0]) null else
                    options.filterIndexed { index, _ -> checked[index + 1] }.toSet())
            }
            .setNeutralButton("添加取件点") { _, _ ->
                val selection = options.filterIndexed { index, _ -> checked[index + 1] }.toSet()
                askText("添加取件点", "输入原始取件点名称或详细地址") { value ->
                    if (value.isBlank()) return@askText false
                    Settings.setIdentityLocations(activity, selection + value)
                    editLocations()
                    true
                }
            }
            .setNegativeButton("取消", null).show()
    }

    private fun clearCache() {
        FrostedDialogBuilder(activity).setTitle("清除缓存")
            .setMessage("清理相机临时文件和应用缓存。包裹、规则、照片与设置会保留。")
            .setPositiveButton("清除") { _, _ ->
                Thread {
                    val cleared = activity.cacheDir.listFiles()?.map { it.deleteRecursively() }?.all { it } ?: true
                    activity.runOnUiThread { toast(if (cleared) "缓存已清除" else "部分缓存正在使用") }
                }.start()
            }.setNegativeButton("取消", null).show()
    }

    private fun askText(title: String, hint: String, initial: String = "", number: Boolean = false, save: (String) -> Boolean) {
        val input = EditText(activity).apply {
            this.hint = hint
            setText(initial)
            setSingleLine()
            if (number) inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val content = LinearLayout(activity).apply {
            val gap = (20 * resources.displayMetrics.density).toInt()
            setPadding(gap, 0, gap, 0)
            addView(input, LinearLayout.LayoutParams(-1, -2))
        }
        val dialog = FrostedDialogBuilder(activity).setTitle(title).setView(content)
            .setPositiveButton("保存", null).setNegativeButton("取消", null).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (save(input.text.toString().trim())) dialog.dismiss()
            else input.error = hint
        }
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    private fun appDetails() {
        open(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}")))
    }

    private fun open(intent: Intent) {
        runCatching { activity.startActivity(intent) }.onFailure { toast("此设备没有对应的页面") }
    }

    private fun homeLabel() = "首页目标 · " + Settings.homePage(activity).title
    private fun splashLabel() = "开屏时长 · " + if (visuals.seconds() == 0) "关闭" else "${visuals.seconds()} 秒"
    private fun overdueLabel() = "超期提醒 · ${Settings.getOverdueDays(activity)} 天"
    private fun toast(message: String) = Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
}

