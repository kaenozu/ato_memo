package jp.neoenox.ato_memo

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.MediaStore
import java.util.concurrent.Executors

object NativeWork { val executor = Executors.newSingleThreadExecutor() }

object MediaJobs {
    const val CHANGES = 71
    const val PERIODIC = 72
    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val settings = Settings(context)
        val service = ComponentName(context, MediaImportJob::class.java)
        val active = settings.auto && settings.mediaAllowed()
        if (!active) scheduler.cancel(CHANGES)
        else if (scheduler.getPendingJob(CHANGES) == null) armChanges(context)
        // Maintenance also runs with auto-import OFF so completed copies can expire.
        val interval = if (active) 15 * 60 * 1000L else 24 * 60 * 60 * 1000L
        if (scheduler.getPendingJob(PERIODIC)?.intervalMillis != interval) scheduler.schedule(JobInfo.Builder(PERIODIC, service).setPeriodic(interval).setPersisted(true).build())
    }
    fun armChanges(context: Context) {
        val settings = Settings(context)
        if (!settings.auto || !settings.mediaAllowed()) return
        context.getSystemService(JobScheduler::class.java).schedule(JobInfo.Builder(CHANGES, ComponentName(context, MediaImportJob::class.java))
            .addTriggerContentUri(JobInfo.TriggerContentUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
            .setTriggerContentUpdateDelay(1000).setTriggerContentMaxDelay(5000).build())
    }
}

class MediaImportJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        NativeWork.executor.execute {
            try { ImageImporter(applicationContext).scan() }
            catch (_: Exception) { Settings(applicationContext).lastError = "自動取り込みに失敗しました。画像のアクセス許可を確認してください。" }
            finally {
                if (params.jobId == MediaJobs.CHANGES) MediaJobs.armChanges(applicationContext)
                jobFinished(params, false)
            }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean = true
}
