package jp.neoenox.ato_memo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private lateinit var channel: MethodChannel
    private var permissionResult: MethodChannel.Result? = null
    private var pickerResult: MethodChannel.Result? = null
    private var openId = -1L
    private var pendingScheduleId = -1L
    private var importError = ""
    private var importing = false
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "ato_memo/native")
        channel.setMethodCallHandler { call, result ->
            when (call.method) {
                "snapshot" -> work(result) {
                    MemoStore(this).use { store ->
                        store.cleanup()
                        val snapshot = mapOf("items" to store.items(), "settings" to Settings(this).map(), "openId" to openId, "scheduleId" to pendingScheduleId, "importing" to importing, "importError" to importError)
                        openId = -1; pendingScheduleId = -1; importError = ""
                        snapshot
                    }
                }
                "saveSettings" -> workHeavy(result) {
                    val old = Settings(this).map()
                    val values = call.arguments as Map<*, *>
                    Settings(this).update(values)
                    if (listOf("hour", "minute", "delayDays", "weekly", "weekday").any { values.containsKey(it) && values[it] != old[it] }) MemoStore(this).use { it.rescheduleDefaults(Settings(this).defaultDue()) }
                    MediaJobs.schedule(this)
                    if (Settings(this).auto && (values["auto"] == true || values.containsKey("allImages"))) ImageImporter(this).scan()
                    ReminderScheduler(this).schedule(); null
                }
                "schedule" -> work(result) {
                    MemoStore(this).use { it.schedule((call.argument<Number>("id")!!).toLong(), (call.argument<Number>("due")!!).toLong()) }
                    ReminderScheduler(this).schedule(); null
                }
                "complete" -> work(result) { MemoStore(this).use { it.complete((call.arguments as Number).toLong()) }; ReminderScheduler(this).clearNotification(); ReminderScheduler(this).schedule(); null }
                "delete" -> work(result) { MemoStore(this).use { it.delete((call.arguments as Number).toLong()) }; ReminderScheduler(this).clearNotification(); ReminderScheduler(this).schedule(); null }
                "retryOcr" -> workHeavy(result) { ImageImporter(this).retryOcr((call.arguments as Number).toLong()); null }
                "scan" -> workHeavy(result) { ImageImporter(this).scan(call.arguments == true) }
                "requestMedia" -> {
                    if (permissionResult != null) result.error("busy", "権限の確認中です", null)
                    else {
                        permissionResult = result
                        val permissions = when {
                            Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
                            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                        requestPermissions(permissions, 41)
                    }
                }
                "requestNotifications" -> {
                    if (Build.VERSION.SDK_INT >= 33) {
                        if (permissionResult != null) result.error("busy", "権限の確認中です", null)
                        else { permissionResult = result; requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42) }
                    } else { startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, packageName)); result.success(null) }
                }
                "exactSettings" -> { if (Build.VERSION.SDK_INT >= 31) startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))); result.success(null) }
                "appSettings" -> { startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))); result.success(null) }
                "pickImages" -> {
                    if (pickerResult != null) result.error("busy", "画像を選択中です", null)
                    else { pickerResult = result; startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE); putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) }, 43) }
                }
                "openUrl" -> { try { val uri = Uri.parse(call.arguments as String); require(uri.scheme in listOf("http", "https")); startActivity(Intent(Intent.ACTION_VIEW, uri)); result.success(null) } catch (_: Exception) { result.error("url", "リンクを開けませんでした", null) } }
                else -> result.notImplemented()
            }
        }
        handleIntent(intent)
    }
    private fun work(result: MethodChannel.Result, action: () -> Any?) {
        NativeWork.ui.execute { try { val value = action(); runOnUiThread { result.success(value) } }
            catch(e: Exception) { runOnUiThread { result.error("native", e.message ?: "処理に失敗しました", null) } } }
    }
    private fun workHeavy(result: MethodChannel.Result, action: () -> Any?) {
        NativeWork.heavy.execute { try { val value = action(); runOnUiThread { result.success(value) } }
            catch(e: Exception) { runOnUiThread { result.error("native", e.message ?: "処理に失敗しました", null) } } }
    }
    private fun changed() { if (::channel.isInitialized) runOnUiThread { channel.invokeMethod("changed", null) } }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleIntent(intent) }
    private fun handleIntent(intent: Intent) {
        openId = intent.getLongExtra("openId", -1)
        intent.removeExtra("openId")
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) { changed(); return }
        val uris = if (intent.action == Intent.ACTION_SEND_MULTIPLE) intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.toList() ?: emptyList()
            else listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        val origin = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        intent.action = Intent.ACTION_MAIN
        if (uris.isEmpty()) { importError = "共有された画像がありません"; changed(); return }
        importUris(uris, origin)
    }
    private fun importUris(uris: List<Uri>, origin: String, result: MethodChannel.Result? = null) {
        importing = true; changed()
        NativeWork.heavy.execute {
            val ids = mutableListOf<Long>(); var failures = 0
            for (uri in uris) try { val id = ImageImporter(this).import(uri, origin); if (id > 0) ids.add(id) } catch (_: Exception) { failures++ }
            ReminderScheduler(this).schedule()
            runOnUiThread {
                importing = false
                if (ids.size == 1) { openId = ids[0]; pendingScheduleId = ids[0] }
                if (failures > 0) importError = "${failures}枚を取り込めませんでした。50MB以下の対応画像か確認してください。"
                result?.success(ids.size); changed()
            }
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 41 && requestCode != 42) return
        val result = permissionResult; permissionResult = null
        val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        if (granted) {
            result?.success(null)
        } else {
            val partial = requestCode == 41 && Build.VERSION.SDK_INT >= 34 &&
                permissions.zip(grantResults.toList()).any { (name, grant) ->
                    name == Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED && grant == PackageManager.PERMISSION_GRANTED
                }
            when {
                partial -> result?.error("partial", "一部のみ許可されています。自動取り込みには全画像へのアクセスが必要です。手動の画像追加は使えます。", null)
                requestCode == 41 -> result?.error("denied", "画像へのアクセスが許可されませんでした。手動の画像追加は使えます。", null)
                else -> result?.error("denied", "通知が許可されませんでした。設定から変更できます。", null)
            }
        }
        ReminderScheduler(this).schedule(); changed()
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 43) {
            val result = pickerResult; pickerResult = null
            if (resultCode != RESULT_OK || data == null) { result?.success(0); return }
            val uris = data.clipData?.let { clip -> (0 until clip.itemCount).map { clip.getItemAt(it).uri } } ?: listOfNotNull(data.data)
            importUris(uris, "", result)
        }
    }
    override fun onResume() {
        super.onResume()
        NativeWork.heavy.execute { try { ImageImporter(this).scan(); ReminderScheduler(this).schedule(); MediaJobs.schedule(this) } catch (_: Exception) { } finally { changed() } }
    }
}
