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
        if (!settings.auto || !settings.mediaAllowed()) {
            scheduler.cancel(CHANGES); scheduler.cancel(PERIODIC); return
        }
        val service = ComponentName(context, MediaImportJob::class.java)
        if (scheduler.getPendingJob(CHANGES) == null) armChanges(context)
        if (scheduler.getPendingJob(PERIODIC) == null) scheduler.schedule(JobInfo.Builder(PERIODIC, service).setPeriodic(15 * 60 * 1000L).setPersisted(true).build())
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
