package jp.neoenox.ato_memo

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MvpDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val sources = mutableListOf<Uri>()
    private var activity: Activity? = null
    @Before fun setup() {
        context.deleteDatabase("memos.db")
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSystemService(NotificationManager::class.java).cancelAll()
        shell("pm grant ${context.packageName} android.permission.READ_MEDIA_IMAGES")
        shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
    }
    @After fun teardown() {
        activity?.let { a -> instrumentation.runOnMainSync { a.finish() } }; activity = null
        sources.forEach { context.contentResolver.delete(it, null, null) }
        MemoStore(context).use { store -> store.items().forEach { store.delete(it["id"] as Long) } }
        Settings(context).update(mapOf("auto" to false)); MediaJobs.schedule(context)
    }
    private fun shell(cmd: String) { instrumentation.uiAutomation.executeShellCommand(cmd).close() }
    private fun image(name: String = "Screenshot_test_${System.nanoTime()}.png", folder: String = "Pictures/Screenshots"): Uri {
        val values = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, "image/png"); put(MediaStore.Images.Media.RELATIVE_PATH, folder); put(MediaStore.Images.Media.IS_PENDING, 1) }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!; sources.add(uri)
        val bitmap = Bitmap.createBitmap(1000, 500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 68f }
        canvas.drawText("10/15まで", 60f, 150f, paint); canvas.drawText("19:00開演", 60f, 280f, paint)
        context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return uri
    }
    @Test fun copySurvivesSourceDeletionAndJapaneseOcrFindsDateOffline() {
        val uri = image(); val id = ImageImporter(context).import(uri, "元情報 https://example.com/event")
        context.contentResolver.delete(uri, null, null); sources.remove(uri)
        MemoStore(context).use { store ->
            val memo = store.items().single(); assertEquals(id, memo["id"])
            assertTrue(File(memo["path"] as String).length() > 0)
            assertEquals("https://example.com/event", memo["url"])
            assertTrue((memo["ocr"] as String).contains("10/15"))
            assertTrue((memo["candidates"] as List<*>).isNotEmpty())
        }
    }
    @Test fun ocrInitializationFailureStillKeepsTheImageAndReservation() {
        val id = ImageImporter(context) { throw IllegalStateException("OCR initialization unavailable") }.import(image())
        MemoStore(context).use { store ->
            val entry = store.items().single()
            assertEquals(id, entry["id"]); assertTrue(File(entry["path"] as String).length() > 0)
            assertTrue((entry["ocrError"] as String).isNotBlank()); assertNotNull(store.nextDue())
        }
    }
    @Test fun autoOnlyImportsNewScreenshotsAndDoesNotReimportDeletedCopy() {
        image(); Settings(context).update(mapOf("auto" to true))
        image(); image("camera_${System.nanoTime()}.png", "Pictures/Camera")
        assertEquals(1, ImageImporter(context).scan()); assertEquals(0, ImageImporter(context).scan())
        MemoStore(context).use { store -> store.delete(store.items().single()["id"] as Long) }
        assertEquals(0, ImageImporter(context).scan())
    }
    @Test fun allImagesAndPastImportAreAvailableButOffDoesNotScan() {
        image("photo_${System.nanoTime()}.png", "Pictures/Camera")
        assertEquals(0, ImageImporter(context).scan())
        Settings(context).update(mapOf("allImages" to true)); assertTrue(ImageImporter(context).scan(true) >= 1)
        MemoStore(context).use { store -> assertTrue(store.items().any { (it["title"] as String).startsWith("photo_") }) }
    }
    @Test fun settingsRescheduleDefaultsButKeepIndividualBooking() {
        val a = ImageImporter(context).import(image()); val b = ImageImporter(context).import(image())
        val custom = System.currentTimeMillis() + 86400000L; val newDefault = System.currentTimeMillis() + 172800000L
        MemoStore(context).use { store ->
            store.schedule(b, custom); store.rescheduleDefaults(newDefault)
            assertEquals(newDefault, store.items().first { it["id"] == a }["due"])
            assertEquals(custom, store.items().first { it["id"] == b }["due"])
        }
    }
    @Test fun actualNotificationContainsNoImageTextAndCompletionSuppressesReminder() {
        val id = ImageImporter(context).import(image())
        MemoStore(context).use { store -> store.writableDatabase.execSQL("UPDATE memos SET due=? WHERE id=?", arrayOf(System.currentTimeMillis() - 1000, id)) }
        ReminderScheduler(context).deliver()
        val notification = context.getSystemService(NotificationManager::class.java).activeNotifications.single().notification
        assertEquals("見返す画像が1枚あります", notification.extras.getString("android.text"))
        val device = UiDevice.getInstance(instrumentation)
        device.openNotification()
        val entry = device.wait(Until.findObject(By.text("見返す画像が1枚あります")), 15000)
        assertNotNull("The actual notification must appear", entry); entry.click()
        assertTrue("Notification must open the image page", device.wait(Until.hasObject(By.desc("画像を拡大")), 15000))
        device.pressBack(); device.pressHome()
        MemoStore(context).use { store -> assertTrue((store.items().single()["delivered"] as Long) > 0); store.schedule(id, System.currentTimeMillis() + 86400000); store.complete(id); assertNull(store.nextDue()) }
    }
    @Test fun shareIntentOpensScheduleAndPreservesSourceUrl() {
        val uri = image()
        activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND; type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "https://example.com/coupon"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
        val device = UiDevice.getInstance(instrumentation)
        assertTrue("Shared screenshot must open scheduling", device.wait(Until.hasObject(By.desc("いつ見返す？")), 20000))
        MemoStore(context).use { store -> assertEquals("https://example.com/coupon", store.items().single()["url"]) }
        device.pressBack()
    }
    @Test fun multipleShareImportsBothImages() {
        val uris = arrayListOf(image(), image())
        activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND_MULTIPLE; type = "image/png"; putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
        val device = UiDevice.getInstance(instrumentation)
        assertTrue(device.wait(Until.hasObject(By.descContains("見返す画像 2枚")), 20000))
        MemoStore(context).use { assertEquals(2, it.items().size) }
    }
    @Test fun groupedNotificationOpensList() {
        ImageImporter(context).import(image()); ImageImporter(context).import(image())
        MemoStore(context).use { it.writableDatabase.execSQL("UPDATE memos SET due=?", arrayOf(System.currentTimeMillis() - 1000)) }
        ReminderScheduler(context).deliver()
        val device = UiDevice.getInstance(instrumentation); device.openNotification()
        val entry = device.wait(Until.findObject(By.text("見返す画像が2枚あります")), 15000)
        assertNotNull(entry); entry.click()
        assertTrue(device.wait(Until.hasObject(By.descContains("見返す画像 2枚")), 15000))
        device.pressHome()
    }
    @Test fun notificationOffKeepsPendingAndCleanupDoesNotDeleteSource() {
        val uri = image(); val id = ImageImporter(context).import(uri)
        Settings(context).update(mapOf("notify" to false))
        MemoStore(context).use { store -> store.writableDatabase.execSQL("UPDATE memos SET due=? WHERE id=?", arrayOf(System.currentTimeMillis() - 1000, id)) }
        ReminderScheduler(context).deliver()
        assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications.isEmpty())
        MemoStore(context).use { store ->
            val copy = File(store.items().single()["path"] as String); assertEquals(0L, store.items().single()["delivered"])
            store.writableDatabase.execSQL("UPDATE memos SET completed=? WHERE id=?", arrayOf(System.currentTimeMillis() - 31L * 86400000, id))
            store.cleanup(); assertTrue(store.items().isEmpty()); assertFalse(copy.exists())
        }
        context.contentResolver.openInputStream(uri)!!.close()
    }
}
