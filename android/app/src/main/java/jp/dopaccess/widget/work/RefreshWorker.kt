package jp.dopaccess.widget.work

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import jp.dopaccess.widget.data.DopaClient
import jp.dopaccess.widget.data.SettingsStore
import jp.dopaccess.widget.widget.DopaWidget
import java.util.concurrent.TimeUnit

class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val store = SettingsStore(applicationContext)
        when (val r = DopaClient(store.settings()).realtime()) {
            is DopaClient.Result.Ok -> store.saveResult(r.body, null)
            is DopaClient.Result.Fail -> store.saveResult(null, r.message)
        }
        DopaWidget().updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "dopaccess-periodic"
        private const val MANUAL = "dopaccess-manual"
        private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(ctx: Context, intervalMin: Int) {
            val req = PeriodicWorkRequestBuilder<RefreshWorker>(intervalMin.coerceAtLeast(15).toLong(), TimeUnit.MINUTES)
                .setConstraints(net)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun refreshNow(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<RefreshWorker>().setConstraints(net).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(MANUAL, ExistingWorkPolicy.KEEP, req)
        }

        fun cancel(ctx: Context) = WorkManager.getInstance(ctx).cancelUniqueWork(PERIODIC)
    }
}
