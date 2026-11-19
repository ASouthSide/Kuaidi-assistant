package com.kuaidi.assistant

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.util.AtomicFile
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.drawerlayout.widget.DrawerLayout
import java.io.File
import java.util.concurrent.Executors

internal class AppVisuals(private val activity: AppCompatActivity) {
    enum class Slot(val fileName: String) {
        BACKGROUND("background.jpg"), SPLASH("splash.jpg")
    }

    private val prefs = activity.getSharedPreferences("visuals", 0)
    private val directory = File(activity.filesDir, "visuals")
    private val worker = Executors.newSingleThreadExecutor()
    private val root = activity.findViewById<FrameLayout>(R.id.dashboard_root)
    val wallpaperView: ImageView = activity.findViewById(R.id.dashboard_wallpaper)
    private val scrim = activity.findViewById<View>(R.id.wallpaper_scrim)
    private val content = activity.findViewById<View>(R.id.dashboard_content)
    private val drawer = activity.findViewById<DrawerLayout>(R.id.directory_drawer)
    private var splash: View? = null
    private var restoreChrome: (() -> Unit)? = null
    private val dismissSplashTask = Runnable { dismissSplash() }
    private var closed = false
    var onWallpaperTexture: (Bitmap?) -> Unit = {}

    fun show(freshLaunch: Boolean) {
        val duration = seconds()
        if (freshLaunch && duration > 0) showSplash(duration)
        refreshBackground()
    }

    fun seconds() = prefs.getInt("splashSeconds", 2).coerceIn(0, 5)
    fun blur(slot: Slot) = prefs.getBoolean("blur_" + slot.name, false)
    fun setSeconds(value: Int) {
        prefs.edit().putInt("splashSeconds", value.coerceIn(0, 5)).apply()
    }

    fun setBlur(slot: Slot, value: Boolean) {
        prefs.edit().putBoolean("blur_" + slot.name, value).apply()
        if (slot == Slot.BACKGROUND) refreshBackground()
    }

    fun savePhoto(slot: Slot, uri: Uri) {
        worker.execute {
            val saved = runCatching {
                directory.mkdirs()
                val image = decode(uri)
                val target = AtomicFile(file(slot))
                val stream = target.startWrite()
                try {
                    check(image.compress(Bitmap.CompressFormat.JPEG, 88, stream))
                    target.finishWrite(stream)
                } catch (failure: Exception) {
                    target.failWrite(stream)
                    throw failure
                } finally {
                    image.recycle()
                }
            }.isSuccess
            activity.runOnUiThread {
                if (!closed) {
                    if (saved && slot == Slot.BACKGROUND) refreshBackground()
                    toast(if (saved) "照片已保存" else "无法读取这张照片")
                }
            }
        }
    }

    fun removePhoto(slot: Slot) {
        worker.execute {
            AtomicFile(file(slot)).delete()
            activity.runOnUiThread {
                if (!closed) {
                    if (slot == Slot.BACKGROUND) refreshBackground()
                    toast("已移除照片")
                }
            }
        }
    }

    private fun refreshBackground() {
        val shouldBlur = blur(Slot.BACKGROUND)
        worker.execute {
            val image = load(Slot.BACKGROUND, shouldBlur)
            val texture = image?.let {
                Bitmap.createScaledBitmap(it, maxOf(1, it.width / 26), maxOf(1, it.height / 26), true)
            }
            activity.runOnUiThread {
                if (!closed && shouldBlur == blur(Slot.BACKGROUND)) {
                    display(wallpaperView, image, shouldBlur)
                    scrim.visibility = if (image != null) View.VISIBLE else View.GONE
                    onWallpaperTexture(texture)
                }
            }
        }
    }

    private fun showSplash(duration: Int) {
        val overlay = activity.layoutInflater.inflate(R.layout.view_startup_splash, root, false)
        val photo = overlay.findViewById<ImageView>(R.id.splash_photo)
        val version = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName.orEmpty()
        overlay.findViewById<TextView>(R.id.splash_app_info).text =
            activity.getString(R.string.splash_app_info, version)
        overlay.findViewById<View>(R.id.splash_skip).setOnClickListener { dismissSplash() }
        splash = overlay
        val accessibility = content.importantForAccessibility
        val drawerMode = drawer.getDrawerLockMode(android.view.Gravity.START)
        val window = activity.window
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        val lightStatus = bars.isAppearanceLightStatusBars
        val lightNav = bars.isAppearanceLightNavigationBars
        val statusColor = window.statusBarColor
        val navColor = window.navigationBarColor
        restoreChrome = {
            content.importantForAccessibility = accessibility
            drawer.setDrawerLockMode(drawerMode, android.view.Gravity.START)
            bars.isAppearanceLightStatusBars = lightStatus
            bars.isAppearanceLightNavigationBars = lightNav
            window.statusBarColor = statusColor
            window.navigationBarColor = navColor
        }
        content.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, android.view.Gravity.START)
        bars.isAppearanceLightStatusBars = true
        bars.isAppearanceLightNavigationBars = true
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        overlay.postDelayed(dismissSplashTask, duration * 1000L)
        val shouldBlur = blur(Slot.SPLASH)
        worker.execute {
            val image = load(Slot.SPLASH, shouldBlur)
            activity.runOnUiThread {
                if (!closed && splash === overlay) display(photo, image, shouldBlur)
                else image?.recycle()
            }
        }
    }

    fun dismissSplash(): Boolean {
        val overlay = splash ?: return false
        splash = null
        overlay.removeCallbacks(dismissSplashTask)
        root.removeView(overlay)
        restoreChrome?.invoke()
        restoreChrome = null
        return true
    }

    private fun display(view: ImageView, bitmap: Bitmap?, blurred: Boolean) {
        view.setImageBitmap(bitmap)
        if (Build.VERSION.SDK_INT >= 31) view.setRenderEffect(
            if (blurred && bitmap != null) RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP)
            else null
        )
    }

    private fun load(slot: Slot, blurred: Boolean): Bitmap? {
        val source = file(slot)
        if (!source.isFile) return null
        val image = BitmapFactory.decodeFile(source.absolutePath) ?: return null
        if (Build.VERSION.SDK_INT >= 31 || !blurred) return image
        return Bitmap.createScaledBitmap(image, maxOf(1, image.width / 28), maxOf(1, image.height / 28), true)
            .also { if (it !== image) image.recycle() }
    }

    private fun decode(uri: Uri): Bitmap {
        val resolver = activity.contentResolver
        if (Build.VERSION.SDK_INT >= 28) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                val divisor = maxOf(1f, maxOf(info.size.width, info.size.height) / 1600f)
                decoder.setTargetSize(maxOf(1, (info.size.width / divisor).toInt()), maxOf(1, (info.size.height / divisor).toInt()))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        require(options.outWidth > 0 && options.outHeight > 0)
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1600) {
            options.inSampleSize *= 2
        }
        options.inJustDecodeBounds = false
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Invalid image")
    }

    private fun file(slot: Slot) = File(directory, slot.fileName)
    private fun toast(message: String) = Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()

    fun close() {
        closed = true
        dismissSplash()
        worker.shutdown()
    }
}

