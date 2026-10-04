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
    fun import(uri: Uri, origin: String = "", sourceKey: String = uri.toString()): Long {
        MemoStore(context).use { store ->
            val existing = store.idForSource(sourceKey)
            if (existing > 0) {
                val entry = store.items().first { it["id"] == existing }
                if (File(entry["path"] as String).isFile) {
                    Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE).find(origin)?.value?.let { store.updateUrl(existing, it) }
                    return existing
                }
                store.delete(existing)
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
                val url = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE).find(origin)?.value ?: ""
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
        try {
            val client = recognizerFactory()
            recognizer = client
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, options)
            var sample = 1
            while (maxOf(options.outWidth, options.outHeight) / sample > 2048) sample *= 2
            options.inJustDecodeBounds = false; options.inSampleSize = sample
            val bitmap = BitmapFactory.decodeFile(file.path, options) ?: error("画像を読み取れません")
            val rotation = try { when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90; ExifInterface.ORIENTATION_ROTATE_180 -> 180; ExifInterface.ORIENTATION_ROTATE_270 -> 270; else -> 0
            } } catch (_: Exception) { 0 }
            val task = client.process(InputImage.fromBitmap(bitmap, rotation))
            // Keep the bitmap alive until ML Kit finishes, including a timeout path.
            task.addOnCompleteListener { bitmap.recycle() }
            val text = Tasks.await(task, 30, TimeUnit.SECONDS).text
            val dates = ReminderTimes.candidates(text, LocalDateTime.now()).map { it.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
            store.ocr(id, text, dates)
        } catch (_: Exception) { store.ocr(id, "", emptyList(), "文字を読み取れませんでした。通知日時は手動で指定できます。") }
        finally { runCatching { recognizer?.close() } }
    }
    fun retryOcr(id: Long) { MemoStore(context).use { store -> store.items().firstOrNull { it["id"] == id }?.let { recognize(store, id, File(it["path"] as String)) } } }
    fun scan(past: Boolean = false): Int {
        MemoStore(context).use { it.cleanup() }
        ReminderScheduler(context).schedule()
        if (!settings.mediaAllowed() || (!past && !settings.auto)) return 0
        val since = if (past) 0L else settings.enabledAt / 1000
        val columns = mutableListOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        if (Build.VERSION.SDK_INT >= 29) columns.add(MediaStore.Images.Media.RELATIVE_PATH)
        val selection = "${MediaStore.Images.Media.DATE_ADDED}>=?" + (if (!past) " AND ${MediaStore.Images.Media._ID}>${settings.baselineId}" else "") + if (Build.VERSION.SDK_INT >= 29) " AND ${MediaStore.Images.Media.IS_PENDING}=0" else ""
        var count = 0
        val failures = mutableListOf<String>()
        context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns.toTypedArray(), selection, arrayOf(since.toString()), "${MediaStore.Images.Media.DATE_ADDED} ASC")?.use { c ->
            while (c.moveToNext()) {
                // A job can outlive the switch: respect disabling auto while it is running.
                if (!past && !Settings(context).auto) break
                val id = c.getLong(0)
                val info = (1 until columns.size).filter { it != 2 }.joinToString(" ") { c.getString(it) ?: "" }.lowercase()
                if (!settings.allImages && listOf("screenshot", "screen_shot", "screen-shot", "スクリーンショット", "スクショ").none { info.contains(it) }) continue
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                try { MemoStore(context).use { store -> if (!store.seen(uri.toString())) { import(uri); count++ } } }
                catch (_: Exception) { failures.add(c.getString(1) ?: "画像") }
            }
        }
        settings.lastError = if (failures.isEmpty()) "" else "${failures.size}枚を取り込めませんでした。画像の状態や空き容量を確認してください。"
        MemoStore(context).use { it.cleanup() }
        ReminderScheduler(context).schedule()
        return count
    }
}
