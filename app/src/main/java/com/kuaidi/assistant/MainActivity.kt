package com.kuaidi.assistant

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.io.File
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: PackageAdapter
    private lateinit var emptyView: TextView
    private lateinit var tvCount: TextView
    private lateinit var spinnerDate: Spinner
    private lateinit var spinnerCarrier: Spinner
    private lateinit var spinnerAddr: Spinner

    private var allPackages: MutableList<Package> = mutableListOf()
    private val aliases = mutableMapOf<String, String>()
    private var setupInProgress = false

    private val dateOptions = listOf("全部", "今天", "近3天", "近7天")

    private var currentPhotoUri: Uri? = null

    // 跳转区「删除模式」：开启后每个应用显示 ×，点击可删除
    private var deleteMode = false

    // 相册选图（截图/购物软件截图）
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) recognizeImage(uri)
    }
    // 拍照（包裹面单）
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) currentPhotoUri?.let { recognizeImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        emptyView = findViewById(R.id.tv_empty)
        tvCount = findViewById(R.id.tv_count)
        spinnerDate = findViewById(R.id.spinner_date)
        spinnerCarrier = findViewById(R.id.spinner_carrier)
        spinnerAddr = findViewById(R.id.spinner_addr)

        val rv: RecyclerView = findViewById(R.id.rv_packages)
        adapter = PackageAdapter(
            mutableListOf(),
            { loc -> PackageStore.displayName(loc, aliases) },
            ::togglePicked,
            ::confirmDelete
        )
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<Button>(R.id.btn_add).setOnClickListener { showAddDialog() }
        findViewById<Button>(R.id.btn_paste).setOnClickListener { showPasteDialog() }
        findViewById<Button>(R.id.btn_idcode).setOnClickListener { openIdentityCode() }
        findViewById<Button>(R.id.btn_addr_mgr).setOnClickListener { showAddressManager() }
        findViewById<Button>(R.id.btn_image).setOnClickListener { showImageSourceDialog() }
        findViewById<Button>(R.id.btn_settings).setOnClickListener { showSettingsDialog() }

        rebuildJumpGrid()

        // 两个区域可折叠
        findViewById<LinearLayout>(R.id.header_packages).setOnClickListener {
            val c = findViewById<LinearLayout>(R.id.content_packages)
            val arrow = findViewById<TextView>(R.id.arrow_packages)
            val collapse = c.visibility == View.VISIBLE
            c.visibility = if (collapse) View.GONE else View.VISIBLE
            arrow.text = if (collapse) "▸" else "▾"
        }
        findViewById<LinearLayout>(R.id.header_jump).setOnClickListener {
            val c = findViewById<LinearLayout>(R.id.jump_container)
            val arrow = findViewById<TextView>(R.id.arrow_jump)
            val collapse = c.visibility == View.VISIBLE
            c.visibility = if (collapse) View.GONE else View.VISIBLE
            arrow.text = if (collapse) "▸" else "▾"
        }

        // 删除模式：点跳转区标题旁的删除图标切换
        findViewById<ImageView>(R.id.btn_edit_jump).setOnClickListener {
            deleteMode = !deleteMode
            Toast.makeText(
                this,
                if (deleteMode) "删除模式：点应用右上角 × 删除" else "已退出删除模式",
                Toast.LENGTH_SHORT
            ).show()
            rebuildJumpGrid()
        }

        // 日期筛选（固定选项，只设一次）
        spinnerDate.adapter = makeAdapter(dateOptions)
        spinnerDate.onItemSelectedListener = filterListener()
        spinnerCarrier.onItemSelectedListener = filterListener()
        spinnerAddr.onItemSelectedListener = filterListener()

        reload()
        requestSmsPermission()
        scheduleDailyOverdueCheck()
    }

    override fun onResume() {
        super.onResume()
        reload()
        OverdueReceiver.checkAndNotify(this)
    }

    // ---------- 数据与筛选 ----------
    private fun reload() {
        allPackages = PackageStore.load(this)
        aliases.clear()
        aliases.putAll(PackageStore.loadAliases(this))
        populateFilters()
        refresh()
    }

    private fun makeAdapter(items: List<String>): ArrayAdapter<String> {
        val a = ArrayAdapter(this, R.layout.spinner_item, items)
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        return a
    }

    private fun populateFilters() {
        setupInProgress = true
        val prevCarrier = spinnerCarrier.selectedItem as? String ?: "全部"
        val prevAddr = spinnerAddr.selectedItem as? String ?: "全部"

        val carriers = listOf("全部") + allPackages.map { it.carrier }.filter { it.isNotBlank() }.distinct()
        spinnerCarrier.adapter = makeAdapter(carriers)

        val addrs = listOf("全部") +
                allPackages.map { PackageStore.displayName(it.location, aliases) }.filter { it.isNotBlank() }.distinct()
        spinnerAddr.adapter = makeAdapter(addrs)

        restoreSelection(spinnerCarrier, carriers, prevCarrier)
        restoreSelection(spinnerAddr, addrs, prevAddr)
        setupInProgress = false
    }

    private fun restoreSelection(spinner: Spinner, items: List<String>, prev: String) {
        val i = items.indexOf(prev)
        if (i >= 0) spinner.setSelection(i)
    }

    private fun filterListener() = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            if (!setupInProgress) refresh()
        }
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    private fun applyFilter(): List<Package> {
        val dateSel = spinnerDate.selectedItem as? String ?: "全部"
        val carrierSel = spinnerCarrier.selectedItem as? String ?: "全部"
        val addrSel = spinnerAddr.selectedItem as? String ?: "全部"
        val now = System.currentTimeMillis()

        return allPackages.filter { p ->
            val dateOk = when (dateSel) {
                "今天" -> isSameDay(p.arriveTime, now)
                "近3天" -> now - p.arriveTime < 3L * 86400000
                "近7天" -> now - p.arriveTime < 7L * 86400000
                else -> true
            }
            val carrierOk = carrierSel == "全部" || p.carrier == carrierSel
            val addrOk = addrSel == "全部" || PackageStore.displayName(p.location, aliases) == addrSel
            dateOk && carrierOk && addrOk
        }
    }

    private fun refresh() {
        adapter.list = applyFilter().toMutableList()
        adapter.notifyDataSetChanged()
        val empty = adapter.list.isEmpty()
        emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        if (::tvCount.isInitialized) {
            tvCount.text = "共 ${adapter.list.size} 件"
        }
    }

    private fun isSameDay(t1: Long, t2: Long): Boolean {
        val c1 = Calendar.getInstance().apply { timeInMillis = t1 }
        val c2 = Calendar.getInstance().apply { timeInMillis = t2 }
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }

    // ---------- 操作 ----------
    private fun togglePicked(p: Package) {
        val markingPicked = !p.picked
        PackageStore.togglePicked(this, p.id)
        allPackages = PackageStore.load(this)
        refresh()
        // 标记为已取时，按设置自动打开淘宝身份码
        if (markingPicked && Settings.getOpenIdentityOnPickup(this)) {
            openIdentityCode()
        }
    }

    private fun confirmDelete(p: Package) {
        AlertDialog.Builder(this)
            .setTitle("删除快件")
            .setMessage("确定删除「${PackageStore.displayName(p.location, aliases)} · 取件码 ${p.pickupCode}」？")
            .setPositiveButton("删除") { _, _ ->
                PackageStore.remove(this, p.id)
                reload()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAddDialog() {
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        val etCode = EditText(this).apply { hint = "取件码（如 3-2-1045）*" }
        val etLoc = EditText(this).apply { hint = "取件点（如 XX校区菜鸟驿站）" }
        val etAddr = EditText(this).apply { hint = "详细地址（可选，如 XX快递服务中心一楼）" }
        val etCarrier = EditText(this).apply { hint = "快递公司（可选，如 中通/圆通）" }
        v.addView(etCode); v.addView(etLoc); v.addView(etAddr); v.addView(etCarrier)

        AlertDialog.Builder(this)
            .setTitle("手动添加快件")
            .setView(v)
            .setPositiveButton("添加") { _, _ ->
                val code = etCode.text.toString().trim()
                if (code.isEmpty()) {
                    Toast.makeText(this, "取件码不能为空", Toast.LENGTH_SHORT).show(); return@setPositiveButton
                }
                PackageStore.add(this, Package(
                    id = System.currentTimeMillis(),
                    pickupCode = code,
                    location = etLoc.text.toString().trim(),
                    address = etAddr.text.toString().trim(),
                    carrier = etCarrier.text.toString().trim(),
                    arriveTime = System.currentTimeMillis()
                ))
                reload()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showPasteDialog() {
        val et = EditText(this).apply {
            hint = "粘贴快递短信全文，自动解析取件码/取件点"
            setPadding(48, 24, 48, 24)
            minLines = 3
        }
        AlertDialog.Builder(this)
            .setTitle("粘贴短信自动解析")
            .setView(et)
            .setPositiveButton("解析并添加") { _, _ ->
                val r = SmsParser.parse(et.text.toString())
                if (r == null) {
                    Toast.makeText(this, "未识别到取件码，请检查短信内容", Toast.LENGTH_SHORT).show()
                } else {
                    PackageStore.add(this, Package(
                        id = System.currentTimeMillis(),
                        pickupCode = r.pickupCode,
                        location = r.location,
                        address = r.address,
                        carrier = r.carrier,
                        arriveTime = System.currentTimeMillis()
                    ))
                    reload()
                    Toast.makeText(this, "已添加：取件码 ${r.pickupCode}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ---------- 地址管理 ----------
    private fun showAddressManager() {
        val locations = allPackages.map { it.location }.filter { it.isNotBlank() }.distinct()
        if (locations.isEmpty()) {
            Toast.makeText(this, "还没有取件点", Toast.LENGTH_SHORT).show(); return
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 8)
        }
        for (loc in locations) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 8) }
            val label = TextView(this).apply {
                text = PackageStore.displayName(loc, aliases)
                textSize = 15f
                setTextColor(0xFF1F2430.toInt())
            }
            val btn = Button(this).apply {
                text = "重命名"
                textSize = 12f
                setOnClickListener { showRenameDialog(loc) }
            }
            row.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(btn)
            container.addView(row)
        }
        val scroll = ScrollView(this).apply { addView(container) }
        AlertDialog.Builder(this)
            .setTitle("地址管理（重命名取件点）")
            .setView(scroll)
            .setPositiveButton("完成", null)
            .show()
    }

    private fun showRenameDialog(loc: String) {
        val et = EditText(this).apply {
            setText(PackageStore.displayName(loc, aliases))
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle("重命名取件点")
            .setMessage("原名称：$loc")
            .setView(et)
            .setPositiveButton("保存") { _, _ ->
                PackageStore.saveAlias(this, loc, et.text.toString().trim())
                reload()
                Toast.makeText(this, "已重命名", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("清除别名") { _, _ ->
                PackageStore.saveAlias(this, loc, "")
                reload()
            }
            .setNeutralButton("取消", null)
            .show()
    }

    // ---------- 身份码 ----------
    private fun openIdentityCode() {
        // 淘宝身份码链式启动（经实测可行的 URL Scheme）
        val urlScheme = "taobao://m.taobao.com/tbopen/index.html?h5Url=https://pages-fast.m.taobao.com/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/identity-code"
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlScheme))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                return
            }
        } catch (_: Exception) {}

        // 兜底：启动淘宝 App 首页
        try {
            val launch = packageManager.getLaunchIntentForPackage("com.taobao.taobao")
            if (launch != null) { startActivity(launch); return }
        } catch (_: Exception) {}

        Toast.makeText(this, "未检测到淘宝 App，请手动打开", Toast.LENGTH_SHORT).show()
    }

    // ---------- 多平台跳转 ----------
    private fun launchApp(pkg: String, name: String) {
        // 仅启动 App，不做快递识别
        try {
            val launch = packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) { startActivity(launch); return }
        } catch (_: Exception) {}
        Toast.makeText(this, "未检测到$name，请先安装", Toast.LENGTH_SHORT).show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /** 动态生成跳转区：数量 ≤4 用 2 列，>4 用 3 列 + 底部「添加应用」 */
    private fun rebuildJumpGrid() {
        val container = findViewById<LinearLayout>(R.id.jump_container)
        container.removeAllViews()

        val items = Settings.loadJumpApps(this)

        // 数量 ≤4 用 2 列；>4 用 3 列（一排最多 3 个）
        val cols = if (items.size <= 4) 2 else 3
        val gap = dp(8)
        val available = resources.displayMetrics.widthPixels - dp(24)
        val cell = (available - gap * (cols - 1)) / cols
        if (cell <= 0) return

        var i = 0
        while (i < items.size) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = gap }

            for (j in 0 until cols) {
                val lp = LinearLayout.LayoutParams(0, cell, 1f)
                if (j > 0) lp.marginStart = gap
                if (i + j < items.size) {
                    val (name, pkg) = items[i + j]
                    row.addView(makeTile(name, pkg), lp)
                } else {
                    row.addView(View(this), lp)
                }
            }
            container.addView(row)
            i += cols
        }

        val addBtn = MaterialButton(this).apply {
            text = "＋ 添加应用"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF3B6EF7.toInt())
            backgroundTintList = ColorStateList.valueOf(0xFFF2F4F8.toInt())
            strokeColor = ColorStateList.valueOf(0xFF3B6EF7.toInt())
            strokeWidth = dp(2)
            cornerRadius = dp(12)
            elevation = dp(2).toFloat()
            setOnClickListener { showAppPicker() }
        }
        container.addView(addBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
        ))
    }

    /** 单个应用方块；删除模式下右上角显示 × */
    private fun makeTile(name: String, pkg: String): FrameLayout {
        val frame = FrameLayout(this)
        val btn = makePlatformButton(name) {
            if (!deleteMode) onPlatformClick(pkg, name)
        }
        frame.addView(btn, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        if (deleteMode) {
            val del = TextView(this).apply {
                text = "×"
                textSize = 13f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#EF4444"))
                }
                setOnClickListener {
                    Settings.removeJumpApp(this@MainActivity, pkg)
                    Toast.makeText(this@MainActivity, "已删除：$name", Toast.LENGTH_SHORT).show()
                    rebuildJumpGrid()
                }
            }
            val lp = FrameLayout.LayoutParams(dp(22), dp(22)).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dp(2)
                rightMargin = dp(2)
            }
            frame.addView(del, lp)
        }
        return frame
    }

    private fun makePlatformButton(name: String, onClick: () -> Unit): MaterialButton {
        return MaterialButton(this).apply {
            text = name
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF3B6EF7.toInt())
            backgroundTintList = ColorStateList.valueOf(0xFFF2F4F8.toInt())
            strokeColor = ColorStateList.valueOf(0xFF3B6EF7.toInt())
            strokeWidth = dp(2)
            cornerRadius = dp(12)
            elevation = dp(2).toFloat()
            setOnClickListener { onClick() }
        }
    }

    /** 点平台按钮：淘宝弹「启动 / 身份码」二选一，其它直接启动 */
    private fun onPlatformClick(pkg: String, name: String) {
        if (pkg == "com.taobao.taobao") {
            AlertDialog.Builder(this)
                .setTitle("淘宝")
                .setItems(arrayOf("启动淘宝", "身份码")) { _, which ->
                    if (which == 0) launchApp(pkg, name) else openIdentityCode()
                }
                .setNegativeButton("取消", null)
                .show()
        } else {
            launchApp(pkg, name)
        }
    }

    /** 选择要链接的手机应用（带搜索） */
    private fun showAppPicker() {
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = try { packageManager.queryIntentActivities(mainIntent, 0) } catch (_: Exception) { emptyList() }
        val existing = Settings.loadJumpApps(this).map { it.second }.toSet()
        val allApps = infos
            .map { it.loadLabel(packageManager).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }
            .filter { it.second !in existing && it.second != packageName }
            .sortedBy { it.first }
        if (allApps.isEmpty()) {
            Toast.makeText(this, "未获取到可添加的应用", Toast.LENGTH_SHORT).show()
            return
        }

        // 带搜索的选应用对话框
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
        }
        val search = EditText(this).apply { hint = "搜索应用名称 / 包名"; setSingleLine(true) }
        val listView = ListView(this)
        view.addView(search)
        view.addView(listView)

        val dialog = AlertDialog.Builder(this)
            .setTitle("选择要添加的应用")
            .setView(view)
            .setNegativeButton("取消", null)
            .create()

        var current = allApps
        val adapter = ArrayAdapter(
            this, android.R.layout.simple_list_item_1,
            current.map { "${it.first}  (${it.second})" }
        )
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, pos, _ ->
            val (label, pkg) = current[pos]
            dialog.dismiss()
            confirmAppName(label, pkg)
        }
        search.doAfterTextChanged { editable ->
            val q = editable?.toString()?.trim()?.lowercase() ?: ""
            current = if (q.isEmpty()) allApps
            else allApps.filter { it.first.lowercase().contains(q) || it.second.lowercase().contains(q) }
            adapter.clear()
            adapter.addAll(current.map { "${it.first}  (${it.second})" })
            adapter.notifyDataSetChanged()
        }

        dialog.show()
    }

    /** 链接后弹出：让用户确认/修改应用名称 */
    private fun confirmAppName(defaultName: String, pkg: String) {
        val et = EditText(this).apply {
            setText(defaultName)
            setSelection(defaultName.length)
        }
        AlertDialog.Builder(this)
            .setTitle("确认应用名称")
            .setMessage("已链接：$pkg")
            .setView(et)
            .setPositiveButton("保存") { _, _ ->
                val name = et.text.toString().trim().ifBlank { defaultName }
                Settings.addJumpApp(this, name, pkg)
                rebuildJumpGrid()
                Toast.makeText(this, "已添加：$name", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ---------- 权限与超期检查 ----------
    private fun requestSmsPermission() {
        val perms = mutableListOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
        }
    }

    private fun scheduleDailyOverdueCheck() {
        try {
            val am = getSystemService(ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, OverdueReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                this, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 3600000L,
                AlarmManager.INTERVAL_DAY,
                pi
            )
        } catch (_: Exception) {}
    }

    // ---------- 图片识别 ----------
    private fun showImageSourceDialog() {
        AlertDialog.Builder(this)
            .setTitle("图片识别快递")
            .setItems(arrayOf("从相册选择", "拍照")) { _, which ->
                if (which == 0) {
                    galleryLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                } else {
                    takePhoto()
                }
            }
            .show()
    }

    private fun takePhoto() {
        try {
            val photoFile = File.createTempFile("kuaidi_", ".jpg", cacheDir)
            currentPhotoUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
            cameraLauncher.launch(currentPhotoUri)
        } catch (e: Exception) {
            Toast.makeText(this, "无法启动相机：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun recognizeImage(uri: Uri) {
        Toast.makeText(this, "正在识别…", Toast.LENGTH_SHORT).show()
        try {
            val image = InputImage.fromFilePath(this, uri)
            val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    val r = SmsParser.parse(result.text)
                    if (r == null) {
                        showRawOcrDialog(result.text)
                    } else {
                        showRecognitionResultDialog(r)
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "识别失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Toast.makeText(this, "识别出错：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** 识别成功：预览可编辑，确认后再入库 */
    private fun showRecognitionResultDialog(r: SmsParser.Result) {
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        val etCode = EditText(this).apply { setText(r.pickupCode); hint = "取件码" }
        val etLoc = EditText(this).apply { setText(r.location); hint = "取件点" }
        val etAddr = EditText(this).apply { setText(r.address); hint = "详细地址" }
        val etCarrier = EditText(this).apply { setText(r.carrier); hint = "快递公司" }
        v.addView(etCode); v.addView(etLoc); v.addView(etAddr); v.addView(etCarrier)

        AlertDialog.Builder(this)
            .setTitle("识别结果 · 请核对修改")
            .setView(v)
            .setPositiveButton("确认入库") { _, _ ->
                val code = etCode.text.toString().trim()
                if (code.isEmpty()) {
                    Toast.makeText(this, "取件码不能为空", Toast.LENGTH_SHORT).show()
                } else {
                    PackageStore.add(this, Package(
                        id = System.currentTimeMillis(),
                        pickupCode = code,
                        location = etLoc.text.toString().trim(),
                        address = etAddr.text.toString().trim(),
                        carrier = etCarrier.text.toString().trim(),
                        arriveTime = System.currentTimeMillis()
                    ))
                    reload()
                    Toast.makeText(this, "已入库：取件码 $code", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 识别失败：展示 OCR 原文，方便手动提取取件码 */
    private fun showRawOcrDialog(rawText: String) {
        val et = EditText(this).apply {
            setText(rawText)
            minLines = 4
            setPadding(48, 24, 48, 24)
            hint = "未解析到取件码，下面是识别出的原文"
        }
        AlertDialog.Builder(this)
            .setTitle("识别原文（未解析到取件码）")
            .setView(et)
            .setPositiveButton("关闭", null)
            .show()
    }

    // ---------- 高级设置 ----------
    private fun showSettingsDialog() {
        val daysList = listOf(3, 4, 5, 7)
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        val cb = CheckBox(this).apply {
            text = "点击取件（标记已取）时自动打开淘宝身份码"
            isChecked = Settings.getOpenIdentityOnPickup(this@MainActivity)
        }
        v.addView(cb)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 16, 0, 0) }
        val label = TextView(this).apply { text = "超期提醒天数：" }
        val sp = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, listOf("3 天", "4 天", "5 天", "7 天"))
            setSelection(daysList.indexOf(Settings.getOverdueDays(this@MainActivity)).coerceAtLeast(0))
        }
        row.addView(label); row.addView(sp)
        v.addView(row)

        AlertDialog.Builder(this)
            .setTitle("高级设置")
            .setView(v)
            .setPositiveButton("保存") { _, _ ->
                Settings.setOpenIdentityOnPickup(this, cb.isChecked)
                Settings.setOverdueDays(this, daysList[sp.selectedItemPosition])
                Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
