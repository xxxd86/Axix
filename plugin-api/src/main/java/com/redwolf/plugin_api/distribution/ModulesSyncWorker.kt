package com.redwolf.plugin_api.distribution

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * add("同步 modules.json（本地mock）") {
 *     kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
 *         val base = "http://10.0.2.2:8080" // 模拟器；真机换成电脑局域网 IP
 *         val r = com.redwolf.axix.distribution.ModulesSyncer.sync(this@DemoLauncherActivity, base)
 *         runOnUiThread {
 *             android.widget.Toast.makeText(
 *                 this@DemoLauncherActivity,
 *                 r.fold(onSuccess = { "同步成功" }, onFailure = { "同步失败: ${'$'}{it.message}" }),
 *                 android.widget.Toast.LENGTH_SHORT
 *             ).show()
 *         }
 *         // 安排周期同步
 *         com.redwolf.axix.distribution.ModulesSyncWorker.schedule(this@DemoLauncherActivity, base)
 *     }
 * }
 */

class ModulesSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val base = inputData.getString(KEY_BASE) ?: return Result.failure()
        return ModulesSyncer.sync(applicationContext, base).fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }


    companion object {
        private const val NAME = "modules_sync_periodic"
        const val KEY_BASE = "base"


        fun schedule(ctx: Context, base: String) {
            val req = PeriodicWorkRequestBuilder<ModulesSyncWorker>(6, TimeUnit.HOURS)
                .setInputData(androidx.work.Data.Builder().putString(KEY_BASE, base).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                NAME, ExistingPeriodicWorkPolicy.UPDATE, req
            )
        }
    }
}