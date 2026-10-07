package jp.neoenox.ato_memo

import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit

class ImageImporter(private val context: Context, private val recognizerFactory: () -> TextRecognizer = {
    TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
}) {
    private val settings = Settings(context)
    companion object {
        const val MAX_SCAN_ROWS = 3000
        const val MAX_IMPORT_PER_RUN = 300
    }
    fun import(uri: Uri, origin: String = "", sourceKey: String = uri.toString()): Long {
        MemoStore(context).use { store ->
            val existing = store.idForSource(sourceKey)
            if (existing > 0) {
                val entry = store.items().firstOrNull { it["id"] == existing }
                if (entry != null && File(entry["path"] as String).isFile) {
                    LinkUtils.findFirst(origin).takeIf { it.isNotEmpty() }?.let { store.updateUrl(existing, it) }
                    return existing
                }
                if (entry != null) store.delete(existing)
            }
            val name = try { context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } } catch (_: Exception) { null }
            val folder = File(context.filesDir, "images").apply { mkdirs() }
            val file = File(folder, "${UUID.randomUUID()}.image")
            var savedId = -1L
            try {
                context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output ->
                    val buffer = ByteArray(65536); var total = 0L
                    while (true) { val count = input.read(buffer); if (count == -1) break; total += count; require(total <= 50L * 1024 * 1024) { "50MB以下の画像を選んでください" }; output.write(buffer, 0, count) }
                } } ?: error("画像を開けません")
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "対応していない画像です" }
                val url = LinkUtils.findFirst(origin)
                val id = store.add(sourceKey, file.path, name ?: "スクショ", url, settings.defaultDue())
                require(id > 0) { "画像を保存できませんでした" }
                savedId = id
                recognize(store, id, file)
                return id
            } catch (e: Exception) {
                if (savedId > 0) {
                    store.ocr(savedId, "", emptyList(), "文字を読み取れませんでした。通知日時は手動で指定できます。")
                    return savedId
                }
                file.delete(); throw e
            }
        }
    }
    private fun recognize(store: MemoStore, id: Long, file: File) {
        var recognizer: TextRecognizer? = null
        var bitmap: android.graphics.Bitmap? = null
        val recycled = java.util.concurrent.atomic.AtomicBoolean(false)
        fun recycleOnce() { if (recycled.compareAndSet(false, true)) runCatching { bitmap?.recycle() } }
        try {
            val client = recognizerFactory()
            recognizer = client
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, options)
            var sample = 1
            while (maxOf(options.outWidth, options.outHeight) / sample > 2048) sample *= 2
            options.inJustDecodeBounds = false; options.inSampleSize = sample
            bitmap = BitmapFactory.decodeFile(file.path, options) ?: error("画像を読み取れません")
            val rotation = try { when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90; ExifInterface.ORIENTATION_ROTATE_180 -> 180; ExifInterface.ORIENTATION_ROTATE_270 -> 270; else -> 0
            } } catch (_: Exception) { 0 }
            val task = client.process(InputImage.fromBitmap(bitmap, rotation))
            task.addOnCompleteListener { recycleOnce() }
            val text = try {
                Tasks.await(task, 30, TimeUnit.SECONDS).text
            } finally {
                recycleOnce()
            }
            val dates = ReminderTimes.candidates(text, LocalDateTime.now()).map { it.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
            store.ocr(id, text, dates)
        } catch (_: Exception) { store.ocr(id, "", emptyList(), "文字を読み取れませんでした。通知日時は手動で指定できます。") }
        finally { recycleOnce(); runCatching { recognizer?.close() } }
    }
    fun retryOcr(id: Long) { MemoStore(context).use { store -> store.items().firstOrNull { it["id"] == id }?.let { recognize(store, id, File(it["path"] as String)) } } }
    fun scan(past: Boolean = false): Int {
        MemoStore(context).use { it.cleanup() }
        ReminderScheduler(context).schedule()
        if (!settings.mediaAllowed() || (!past && !settings.auto)) return 0
        val columns = mutableListOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        if (Build.VERSION.SDK_INT >= 29) columns.add(MediaStore.Images.Media.RELATIVE_PATH)
        // The seen table, rather than an enable-time cutoff, prevents duplicates.
        // This also includes existing images for users upgrading with auto already ON.
        val selection = if (Build.VERSION.SDK_INT >= 29) "${MediaStore.Images.Media.IS_PENDING}=0" else null
        var count = 0
        val failures = mutableListOf<String>()
        val encountered = mutableSetOf<String>()
        // Phase 1: read MediaStore rows and close the cursor first (#8).
        data class Row(val uri: Uri, val displayName: String?, val bucket: String?, val relative: String?)
        val rows = mutableListOf<Row>()
        context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns.toTypedArray(), selection, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { c ->
            val hasRelative = columns.contains(MediaStore.Images.Media.RELATIVE_PATH)
            var scanned = 0
            while (c.moveToNext() && scanned < MAX_SCAN_ROWS) {
                scanned++
                val id = c.getLong(0)
                val displayName = c.getString(1)
                val bucket = c.getString(3)
                val relative = if (hasRelative) c.getString(4) else null
                if (!settings.allImages && !ScreenshotFilter.isScreenshot(displayName, bucket, relative)) continue
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                rows.add(Row(uri, displayName, bucket, relative))
            }
        }
        // Phase 2: single store for seen checks; import() manages its own write (#8).
        MemoStore(context).use { store ->
            for ((index, row) in rows.withIndex()) {
                if (count >= MAX_IMPORT_PER_RUN) break
                // A job can outlive the switch: respect disabling auto while it is running.
                if (index % 20 == 0 && !past && !Settings(context).auto) break
                val key = row.uri.toString()
                if (encountered.size < 1000) encountered.add(key)
                if (store.seen(key)) continue
                try { if (import(row.uri) > 0) count++ }
                catch (_: Exception) { failures.add(row.displayName ?: "画像") }
            }
        }
        settings.lastError = if (failures.isEmpty()) "" else "${failures.size}枚を取り込めませんでした。画像の状態や空き容量を確認してください。"
        MemoStore(context).use { it.cleanup(); it.pruneSeen(encountered) }
        ReminderScheduler(context).schedule()
        return count
    }
}
