package com.redwolf.axix

import android.app.Activity
import android.content.Intent
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus


class FeatureLoader(private val activity: Activity) {
    private val manager: SplitInstallManager = SplitInstallManagerFactory.create(activity)
    private var sessionId: Int = 0


    private val listener = SplitInstallStateUpdatedListener { state ->
        if (state.sessionId() != sessionId) return@SplitInstallStateUpdatedListener
        when (state.status()) {
            SplitInstallSessionStatus.PENDING -> { /* 可更新 UI */ }
            SplitInstallSessionStatus.DOWNLOADING -> {
                val total = state.totalBytesToDownload().coerceAtLeast(1)
                val progress = 100 * state.bytesDownloaded() / total
// TODO: 显示进度 progress
            }
            SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
// 某些条件下需要用户确认（如移动网络/大体积）
                manager.startConfirmationDialogForResult(state, activity, 1001)
            }
            SplitInstallSessionStatus.INSTALLED -> {
// 安装完成后尝试启动
                pendingLaunchClassName?.let { launchActivity(it) }
            }
            SplitInstallSessionStatus.FAILED -> {
// TODO: 给出错误提示 state.errorCode()
            }
            else -> {}
        }
    }


    private var pendingLaunchClassName: String? = null


    fun register() = manager.registerListener(listener)
    fun unregister() = manager.unregisterListener(listener)


    fun ensureInstalledAndLaunch(splitId: String, activityClassName: String) {
        if (manager.installedModules.contains(splitId)) {
            launchActivity(activityClassName)
            return
        }
        pendingLaunchClassName = activityClassName
        val request = SplitInstallRequest.newBuilder().addModule(splitId).build()
        manager.startInstall(request)
            .addOnSuccessListener { sessionId = it }
            .addOnFailureListener { /* TODO: 错误处理 */ }
    }


    fun deferredInstall(splitIds: List<String>) {
        manager.deferredInstall(splitIds)
    }


    fun deferredUninstall(splitIds: List<String>) {
        manager.deferredUninstall(splitIds)
    }


    private fun launchActivity(className: String) {
        runCatching {
            val intent = Intent().setClassName(activity, className)
            activity.startActivity(intent)
        }.onFailure {
// 混淆可能导致类名变化，需在 proguard 中 keep（示例见上）
        }
    }
}