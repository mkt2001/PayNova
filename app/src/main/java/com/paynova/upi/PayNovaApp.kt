package com.paynova.upi

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.paynova.upi.work.StatusRefreshWorker
import java.util.concurrent.TimeUnit

class PayNovaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        val req = PeriodicWorkRequestBuilder<StatusRefreshWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("tx-refresh", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}
