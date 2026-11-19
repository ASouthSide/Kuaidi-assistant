package com.kuaidi.assistant

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import androidx.lifecycle.ViewModelProvider

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: PackageAdapter
    private val toggledPickupRows = mutableSetOf<String>()
    private lateinit var emptyView: TextView
    private lateinit var tvCount: TextView
    private lateinit var spinnerDate: Spinner
    private lateinit var spinnerCarrier: Spinner
    private lateinit var spinnerAddr: Spinner
    private lateinit var packagesPage: View
    private lateinit var shortcutsPage: View
    private lateinit var statusFilter: MaterialButtonToggleGroup

    private val inbox by lazy { ViewModelProvider(this)[PackageInboxViewModel::class.java] }
    private val allPackages get() = inbox.packages
    private val aliases get() = inbox.aliases
    private var setupInProgress = false

    private val dateOptions = listOf("全部", "今天", "近3天", "近7天")

    private val imagePickup = ImagePickupController(this) { pkg ->
        inbox.add(pkg)
        reload()
    }

    // 跳转区「删除模式」：开启后每个应用显示 ×，点击可删除
    private var deleteMode = false

    private lateinit var deck: ViewPager2
    private lateinit var navigationOrder: NavigationOrder
    private lateinit var preferencesPage: PreferencesPage
    private lateinit var visuals: AppVisuals
    private var photoSlot = AppVisuals.Slot.BACKGROUND
    private val themePicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { visuals.savePhoto(photoSlot, it) }
    }
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        preferencesPage.refreshPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        deck = findViewById(R.id.dashboard_deck)
        navigationOrder = NavigationOrder.load(this)
        photoSlot = savedInstanceState?.getString("photo_slot")?.let(AppVisuals.Slot::valueOf)
            ?: AppVisuals.Slot.BACKGROUND
        visuals = AppVisuals(this)
        preferencesPage = PreferencesPage(
            this,
            visuals,
            ::chooseThemePhoto,
            ::goHome,
            { permission -> permissionRequest.launch(permission) }
        )
        packagesPage = layoutInflater.inflate(R.layout.page_packages, deck, false)
        shortcutsPage = layoutInflater.inflate(R.layout.page_shortcuts, deck, false)
        val pages = listOf(packagesPage, shortcutsPage, RulePage(this).view, preferencesPage.view)
        val glassPanels = pages.map { FrostedPanelBackground(it, visuals.wallpaperView, dp(24), dp(7)) }
        visuals.onWallpaperTexture = { texture -> glassPanels.forEach { it.setTexture(texture) } }
        setupDeck(pages)
        visuals.show(savedInstanceState == null)
        imagePickup.restore(savedInstanceState)

        emptyView = packagesPage.findViewById(R.id.tv_empty)
        tvCount = packagesPage.findViewById(R.id.tv_count)
        spinnerDate = packagesPage.findViewById(R.id.spinner_date)
        spinnerCarrier = packagesPage.findViewById(R.id.spinner_carrier)
        spinnerAddr = packagesPage.findViewById(R.id.spinner_addr)
        statusFilter = packagesPage.findViewById(R.id.status_filter)
        statusFilter.check(savedInstanceState?.getInt("package_status") ?: R.id.status_all)
        statusFilter.addOnButtonCheckedListener { _, _, checked -> if (checked) refresh() }

        val rv: RecyclerView = packagesPage.findViewById(R.id.rv_packages)
        toggledPickupRows.addAll(savedInstanceState?.getStringArrayList("open_pickup_groups").orEmpty())
        adapter = PackageAdapter(
            { key ->
                if (!toggledPickupRows.add(key)) toggledPickupRows.remove(key)
                refresh()
            },
            ::togglePicked,
            ::confirmDelete
        )
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        packagesPage.findViewById<Button>(R.id.btn_add).setOnClickListener { showAddDialog() }
        packagesPage.findViewById<Button>(R.id.btn_paste).setOnClickListener { showPasteDialog() }
        findViewById<Button>(R.id.btn_idcode).setOnClickListener { openIdentityCode() }
        packagesPage.findViewById<Button>(R.id.btn_addr_mgr).setOnClickListener { showAddressManager() }
        packagesPage.findViewById<Button>(R.id.btn_image).setOnClickListener { imagePickup.showImageSourceDialog() }

        rebuildJumpGrid()

        shortcutsPage.findViewById<MaterialButton>(R.id.btn_edit_jump).setOnClickListener {
            deleteMode = !deleteMode
            refreshShortcutEdit()
        }

        // 日期筛选（固定选项，只设一次）
        spinnerDate.adapter = makeAdapter(dateOptions)
        spinnerDate.onItemSelectedListener = filterListener()
        spinnerCarrier.onItemSelectedListener = filterListener()
        spinnerAddr.onItemSelectedListener = filterListener()

        reload()
        OverdueScheduler.schedule(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("package_status", statusFilter.checkedButtonId)
        outState.putStringArrayList("open_pickup_groups", ArrayList(toggledPickupRows))
        outState.putString("photo_slot", photoSlot.name)
        imagePickup.save(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        reload()
        preferencesPage.refreshPermissions()
        OverdueReceiver.checkAndNotify(this)
    }

    // ---------- 数据与筛选 ----------
    private fun reload() {
        inbox.reload()
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
        val picked = when (statusFilter.checkedButtonId) {
            R.id.status_pending -> false
            R.id.status_picked -> true
            else -> null
        }
        return inbox.filter(dateSel, carrierSel, addrSel, picked)
    }

    private fun refresh() {
        val filtered = applyFilter()
        adapter.submitList(packageRows(filtered, toggledPickupRows, allPackages) { location ->
            PackageStore.displayName(location, aliases)
        })
        emptyView.setText(if (allPackages.isEmpty()) R.string.empty_inbox else R.string.empty_filtered)
        val empty = filtered.isEmpty()
        emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        if (::tvCount.isInitialized) {
            tvCount.text = "${filtered.size} 件"
        }
    }

    // ---------- 操作 ----------
    private fun togglePicked(p: Package) {
        val markingPicked = !p.picked
        inbox.togglePicked(p.id)
        refresh()
        // 标记为已取时，按设置自动打开淘宝身份码
        if (markingPicked && Settings.shouldOpenIdentity(this, p.location, p.address)) {
            openIdentityCode()
        }
    }

    private fun confirmDelete(p: Package) {
        FrostedDialogBuilder(this)
            .setTitle("删除快件")
            .setMessage("确定删除「${PackageStore.displayName(p.location, aliases)} · 取件码 ${p.pickupCode}」？")
            .setPositiveButton("删除") { _, _ ->
                inbox.remove(p.id)
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

        FrostedDialogBuilder(this)
            .setTitle("手动添加快件")
            .setView(v)
            .setPositiveButton("添加") { _, _ ->
                val code = etCode.text.toString().trim()
                if (code.isEmpty()) {
                    Toast.makeText(this, "取件码不能为空", Toast.LENGTH_SHORT).show(); return@setPositiveButton
                }
                inbox.add(Package(
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
        FrostedDialogBuilder(this)
            .setTitle("粘贴短信自动解析")
            .setView(et)
            .setPositiveButton("解析并添加") { _, _ ->
                val r = SmsRules.parse(this, et.text.toString())
                if (r == null) {
                    Toast.makeText(this, "未识别到取件码，请检查短信内容", Toast.LENGTH_SHORT).show()
                } else {
                    inbox.add(Package(
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
        FrostedDialogBuilder(this)
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
        FrostedDialogBuilder(this)
            .setTitle("重命名取件点")
            .setMessage("原名称：$loc")
            .setView(et)
            .setPositiveButton("保存") { _, _ ->
                inbox.saveAlias(loc, et.text.toString().trim())
                reload()
                Toast.makeText(this, "已重命名", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("清除别名") { _, _ ->
                inbox.saveAlias(loc, "")
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
    private fun setupDeck(pages: List<View>) {
        val pageViews = DashboardPage.entries.zip(pages).toMap()
        fun attachCards() {
            val order = navigationOrder[NavigationArea.CARDS]
            deck.adapter = DashboardDeckAdapter(order.map(pageViews::getValue))
            order.forEachIndexed { index, page ->
                pageViews.getValue(page).findViewById<DeckScrollHost>(R.id.deck_scroll_host)?.onSwipeDownAtTop =
                    if (index == 0) null else {
                        {
                            if (currentDashboardPage() == page) openPage(order[index - 1])
                        }
                    }
            }
        }
        attachCards()
        deck.offscreenPageLimit = 1
        deck.getChildAt(0)?.overScrollMode = View.OVER_SCROLL_NEVER
        deck.setPageTransformer { page, position ->
            val distance = kotlin.math.abs(position).coerceAtMost(1f)
            page.translationY = dp(8) * position
            page.scaleX = 1f - 0.06f * distance
            page.scaleY = 1f - 0.04f * distance
            page.alpha = 1f - 0.2f * distance
        }

        val bottomBar = findViewById<LinearLayout>(R.id.deck_navigation)
        val homeButton = findViewById<View>(R.id.deck_home)
        val tabs = DashboardPage.entries.associateWith { destination ->
            findViewById<MaterialButton>(destination.tabId).apply {
                isCheckable = true
                setOnClickListener {
                    isChecked = true
                    openPage(destination)
                }
            }
        }
        homeButton.setOnClickListener { goHome() }
        val drawer = findViewById<DrawerLayout>(R.id.directory_drawer)
        val directory = findViewById<LinearLayout>(R.id.directory_items)
        val directoryLinks = findViewById<LinearLayout>(R.id.directory_links)
        val header = PageControls(this, findViewById(R.id.directory_header))
        val strongColor = android.graphics.Color.rgb(24, 43, 69)
        header.heading(getString(R.string.app_name)).setTextColor(strongColor)
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        header.text(getString(R.string.home_subtitle, version)).setTextColor(strongColor)
        header.heading("目录").setTextColor(strongColor)
        val directoryButtons = DashboardPage.entries.associateWith { destination ->
            MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = destination.title
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                iconGravity = MaterialButton.ICON_GRAVITY_START
                iconPadding = dp(12)
                setIconResource(destination.drawerIcon)
                iconTint = android.content.res.ColorStateList.valueOf(strongColor)
                minHeight = dp(52)
                setTextColor(strongColor)
                strokeColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.rgb(171, 193, 221))
                setOnClickListener {
                    drawer.closeDrawer(directory)
                    openPage(destination)
                }
            }
        }

        fun renderMenus() {
            bottomBar.removeAllViews()
            navigationOrder[NavigationArea.BOTTOM].forEachIndexed { index, page ->
                if (index == 2) bottomBar.addView(homeButton)
                bottomBar.addView(tabs.getValue(page))
            }
            directoryLinks.removeAllViews()
            navigationOrder[NavigationArea.DIRECTORY].forEach { page ->
                directoryLinks.addView(directoryButtons.getValue(page), LinearLayout.LayoutParams(-1, -2))
            }
        }

        fun updateNavigation() {
            val active = currentDashboardPage()
            tabs.forEach { (page, button) -> button.isChecked = page == active }
            directoryButtons.forEach { (page, button) ->
                val selected = page == active
                button.isSelected = selected
                button.strokeWidth = dp(if (selected) 2 else 1)
                button.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    if (selected) android.graphics.Color.rgb(218, 233, 253)
                    else android.graphics.Color.rgb(248, 251, 255)
                )
            }
        }

        findViewById<View>(R.id.edit_navigation).setOnClickListener {
            NavigationOrderEditor(this).show(navigationOrder) { updated ->
                val current = currentDashboardPage()
                val cardsChanged = navigationOrder[NavigationArea.CARDS] != updated[NavigationArea.CARDS]
                navigationOrder = updated
                updated.save(this)
                if (cardsChanged) {
                    attachCards()
                    openPage(current, animate = false)
                }
                renderMenus()
                updateNavigation()
            }
        }
        findViewById<View>(R.id.btn_directory).setOnClickListener { drawer.openDrawer(directory) }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    visuals.dismissSplash() -> Unit
                    drawer.isDrawerOpen(directory) -> drawer.closeDrawer(directory)
                    currentDashboardPage() != DashboardPage.PACKAGES -> openPage(DashboardPage.PACKAGES)
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })
        deck.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateNavigation()
                if (currentDashboardPage() != DashboardPage.SHORTCUTS && deleteMode) {
                    deleteMode = false
                    refreshShortcutEdit()
                }
            }
        })
        renderMenus()
        updateNavigation()
    }

    private fun currentDashboardPage(): DashboardPage =
        navigationOrder[NavigationArea.CARDS][deck.currentItem]

    private fun openPage(destination: DashboardPage, animate: Boolean = true) {
        deck.setCurrentItem(navigationOrder[NavigationArea.CARDS].indexOf(destination), animate)
    }

    private fun goHome() {
        openPage(Settings.homePage(this))
    }

    private class DashboardDeckAdapter(private val pages: List<View>) :
        RecyclerView.Adapter<DashboardDeckAdapter.PageHolder>() {

        class PageHolder(val frame: FrameLayout) : RecyclerView.ViewHolder(frame)

        override fun getItemCount(): Int = pages.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder =
            PageHolder(FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
            })

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            holder.frame.removeAllViews()
            val page = pages[position]
            (page.parent as? ViewGroup)?.removeView(page)
            holder.frame.addView(page, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
    }

    private fun refreshShortcutEdit() {
        shortcutsPage.findViewById<MaterialButton>(R.id.btn_edit_jump).text = if (deleteMode) "完成" else "编辑"
        rebuildJumpGrid()
    }

    private fun rebuildJumpGrid() {
        val container = shortcutsPage.findViewById<LinearLayout>(R.id.jump_container)
        container.removeAllViews()
        val columns = if (resources.configuration.screenWidthDp >= 480) 3 else 2
        val tiles = Settings.loadJumpApps(this).map { (name, pkg) ->
            MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = if (deleteMode) "移除 $name" else name
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setOnClickListener {
                    if (deleteMode) {
                        FrostedDialogBuilder(this@MainActivity)
                            .setMessage("从快捷应用移除「$name」？")
                            .setPositiveButton("移除") { _, _ ->
                                Settings.removeJumpApp(this@MainActivity, pkg)
                                rebuildJumpGrid()
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    } else {
                        onPlatformClick(pkg, name)
                    }
                }
            }
        }.toMutableList()
        if (!deleteMode) {
            tiles += MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "添加应用"
                setOnClickListener { showAppPicker() }
            }
        }
        tiles.chunked(columns).forEach { items ->
            val row = LinearLayout(this)
            repeat(columns) { column ->
                val params = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                    if (column > 0) marginStart = dp(8)
                }
                row.addView(items.getOrNull(column) ?: View(this), params)
            }
            container.addView(row)
        }

    }

    /** 点平台按钮：淘宝弹「启动 / 身份码」二选一，其它直接启动 */
    private fun onPlatformClick(pkg: String, name: String) {
        if (pkg == "com.taobao.taobao") {
            FrostedDialogBuilder(this)
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

        val dialog = FrostedDialogBuilder(this)
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
        FrostedDialogBuilder(this)
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

    private fun chooseThemePhoto(slot: AppVisuals.Slot) {
        photoSlot = slot
        themePicker.launch(androidx.activity.result.PickVisualMediaRequest(
            ActivityResultContracts.PickVisualMedia.ImageOnly
        ))
    }

    override fun onDestroy() {
        visuals.close()
        super.onDestroy()
    }
}
