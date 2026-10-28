package com.srizwan.task.push

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

/** Background check for new notices (see [Push]). Always reports success; the next period tries again after a failure. */
class PushWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        try {
            Push.poll(applicationContext, notify = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // offline / server error — tried again in 15 minutes
        }
        return Result.success()
    }
}
