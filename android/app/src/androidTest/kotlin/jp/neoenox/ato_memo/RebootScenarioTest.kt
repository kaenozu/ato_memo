package jp.neoenox.ato_memo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import java.io.File

/** Run explicitly after MvpDeviceTest. Leaves a pending reminder for the external reboot acceptance check. */
class RebootScenarioTest {
    @Test fun prepare() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.deleteDatabase("memos.db")
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        for (command in listOf("pm grant ${context.packageName} android.permission.READ_MEDIA_IMAGES", "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS", "appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow")) {
            instrumentation.uiAutomation.executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        }
        val image = File(context.cacheDir, "Screenshot_reboot.png")
        val bitmap = Bitmap.createBitmap(1000, 500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(Color.WHITE)
        canvas.drawText("10/15まで 19:00開演", 30f, 180f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 58f })
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val id = ImageImporter(context).import(Uri.fromFile(image)); image.delete()
        Settings(context).update(mapOf("auto" to true))
        MemoStore(context).use { it.schedule(id, System.currentTimeMillis() + 150000) }
        ReminderScheduler(context).schedule(); MediaJobs.schedule(context)
    }
}
