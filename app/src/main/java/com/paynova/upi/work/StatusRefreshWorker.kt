package com.paynova.upi.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.paynova.upi.Graph

/** Periodically re-checks PENDING payments with the provider so the final status always arrives. */
class StatusRefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        Graph.repo.refreshPending()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
