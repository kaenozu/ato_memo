package jp.neoenox.ato_memo

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.time.LocalDateTime
import java.time.ZoneId

class Settings(private val context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val auto get() = prefs.getBoolean("auto", false)
    val allImages get() = prefs.getBoolean("allImages", false)
    val notify get() = prefs.getBoolean("notify", true)
    val hour get() = prefs.getInt("hour", 20)
    val minute get() = prefs.getInt("minute", 0)
    val delayDays get() = prefs.getInt("delayDays", 1)
    val weekly get() = prefs.getBoolean("weekly", false)
    val weekday get() = prefs.getInt("weekday", 6)
    var lastError: String
        get() = prefs.getString("lastError", "") ?: ""
        set(value) { prefs.edit().putString("lastError", value).apply() }
    fun mediaAllowed(): Boolean = context.checkSelfPermission(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    fun notificationsAllowed() = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    fun exactAllowed() = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    fun defaultDue(now: LocalDateTime = LocalDateTime.now()): Long = ReminderTimes.defaultTime(now, hour, minute, delayDays, weekly, weekday).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    fun map(): Map<String, Any> = mapOf("auto" to auto, "allImages" to allImages, "notify" to notify, "hour" to hour, "minute" to minute,
        "delayDays" to delayDays, "weekly" to weekly, "weekday" to weekday, "mediaAllowed" to mediaAllowed(), "notificationsAllowed" to notificationsAllowed(), "exactAllowed" to exactAllowed(), "lastError" to lastError)
    fun update(values: Map<*, *>) {
        val editor = prefs.edit()
        for (key in listOf("auto", "allImages", "notify", "weekly")) (values[key] as? Boolean)?.let { editor.putBoolean(key, it) }
        for ((key, range) in mapOf("hour" to 0..23, "minute" to 0..59, "delayDays" to 0..1, "weekday" to 1..7)) {
            (values[key] as? Number)?.toInt()?.let { require(it in range); editor.putInt(key, it) }
        }
        if (values["auto"] == true && !auto) {
            require(mediaAllowed()) { "全画像へのアクセスを許可してください" }
        }
        editor.commit()
    }
}
