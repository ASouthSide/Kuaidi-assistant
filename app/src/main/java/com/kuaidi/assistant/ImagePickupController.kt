package com.kuaidi.assistant

import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.io.File

internal class ImagePickupController(
    private val activity: AppCompatActivity,
    private val onPackageAdded: (Package) -> Unit
) {
    private var currentPhotoUri: Uri? = null

    fun restore(state: Bundle?) {
        currentPhotoUri = state?.getString("pickup_photo_uri")?.let(Uri::parse)
    }

    fun save(state: Bundle) {
        state.putString("pickup_photo_uri", currentPhotoUri?.toString())
    }

    // 相册选图（截图/购物软件截图）
    private val galleryLauncher = activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) recognizeImage(uri)
    }
    // 拍照（包裹面单）
    private val cameraLauncher = activity.registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) currentPhotoUri?.let { recognizeImage(it) }
    }

    // ---------- 图片识别 ----------
    fun showImageSourceDialog() {
        FrostedDialogBuilder(activity)
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
            val photoFile = File.createTempFile("kuaidi_", ".jpg", activity.cacheDir)
            currentPhotoUri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", photoFile)
            cameraLauncher.launch(currentPhotoUri)
        } catch (e: Exception) {
            Toast.makeText(activity, "无法启动相机：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun recognizeImage(uri: Uri) {
        Toast.makeText(activity, "正在识别…", Toast.LENGTH_SHORT).show()
        try {
            val image = InputImage.fromFilePath(activity, uri)
            val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            recognizer.process(image)
                .addOnCompleteListener { recognizer.close() }
                .addOnSuccessListener(activity) { result ->
                    val r = SmsRules.parse(activity, result.text)
                    if (r == null) {
                        showRawOcrDialog(result.text)
                    } else {
                        showRecognitionResultDialog(r)
                    }
                }
                .addOnFailureListener(activity) { e ->
                    Toast.makeText(activity, "识别失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Toast.makeText(activity, "识别出错：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** 识别成功：预览可编辑，确认后再入库 */
    private fun showRecognitionResultDialog(r: SmsParser.Result) {
        val v = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        val etCode = EditText(activity).apply { setText(r.pickupCode); hint = "取件码" }
        val etLoc = EditText(activity).apply { setText(r.location); hint = "取件点" }
        val etAddr = EditText(activity).apply { setText(r.address); hint = "详细地址" }
        val etCarrier = EditText(activity).apply { setText(r.carrier); hint = "快递公司" }
        v.addView(etCode); v.addView(etLoc); v.addView(etAddr); v.addView(etCarrier)

        FrostedDialogBuilder(activity)
            .setTitle("识别结果 · 请核对修改")
            .setView(v)
            .setPositiveButton("确认入库") { _, _ ->
                val code = etCode.text.toString().trim()
                if (code.isEmpty()) {
                    Toast.makeText(activity, "取件码不能为空", Toast.LENGTH_SHORT).show()
                } else {
                    onPackageAdded(Package(
                        id = System.currentTimeMillis(),
                        pickupCode = code,
                        location = etLoc.text.toString().trim(),
                        address = etAddr.text.toString().trim(),
                        carrier = etCarrier.text.toString().trim(),
                        arriveTime = System.currentTimeMillis()
                    ))
                    Toast.makeText(activity, "已入库：取件码 $code", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 识别失败：展示 OCR 原文，方便手动提取取件码 */
    private fun showRawOcrDialog(rawText: String) {
        val et = EditText(activity).apply {
            setText(rawText)
            minLines = 4
            setPadding(48, 24, 48, 24)
            hint = "未解析到取件码，下面是识别出的原文"
        }
        FrostedDialogBuilder(activity)
            .setTitle("识别原文（未解析到取件码）")
            .setView(et)
            .setPositiveButton("关闭", null)
            .show()
    }
}
