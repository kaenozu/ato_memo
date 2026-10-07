package jp.neoenox.ato_memo

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class ReminderScheduler(private val context: Context) {
    private val settings = Settings(context)
    private val alarm get() = context.getSystemService(AlarmManager::class.java)
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private fun pending() = PendingIntent.getBroadcast(context, 1, Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun schedule() {
        manager.createNotificationChannel(NotificationChannel("reminders", "見返す通知", NotificationManager.IMPORTANCE_DEFAULT))
        alarm.cancel(pending())
        if (!settings.notify || !settings.notificationsAllowed()) return
        val due = MemoStore(context).use { it.nextDue() } ?: return
        val at = maxOf(due, System.currentTimeMillis() + 1000)
        try {
            if (settings.exactAllowed()) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending())
            else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending())
        } catch (_: SecurityException) { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending()) }
    }
    fun deliver() {
        if (!settings.notify || !settings.notificationsAllowed()) return
        MemoStore(context).use { store ->
            val ids = store.due(System.currentTimeMillis())
            if (ids.isEmpty()) { schedule(); return }
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("openId", if (ids.size == 1) ids[0] else -2L)
            }
            val open = PendingIntent.getActivity(context, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.notify(1, Notification.Builder(context, "reminders")
                .setSmallIcon(R.drawable.ic_reminder).setContentTitle("あとメモ")
                .setContentText("見返す画像が${ids.size}枚あります")
                .setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(open).setAutoCancel(true).build())
            store.delivered(ids)
        }
        schedule()
    }
    fun clearNotification() = manager.cancel(1)
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        NativeWork.ui.execute { try { ReminderScheduler(context).deliver() } finally { pending.finish() } }
    }
}

class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        NativeWork.ui.execute {
            try { ReminderScheduler(context).schedule(); MediaJobs.schedule(context) }
            finally { pending.finish() }
        }
    }
}
