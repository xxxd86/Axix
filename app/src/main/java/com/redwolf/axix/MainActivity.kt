package com.redwolf.axix

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.play.core.ktx.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import com.redwolf.axix.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var loader: FeatureLoader
    private val splitId = "login" // 和 manifest android:name 保持一致
    private val loginActivity = "com.redwolf.feature_login.LoginActivity"

    private var sessionId = 0
    private val manager by lazy { SplitInstallManagerFactory.create(this) }
    private val listener =
        com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener { state ->
            if (state.sessionId() != sessionId) return@SplitInstallStateUpdatedListener
            when (state.status()) {
                SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                    manager.startConfirmationDialogForResult(state, this, 1001)
                }

                SplitInstallSessionStatus.INSTALLED -> {
                    startActivity(Intent().setClassName(packageName, loginActivity))
                }

                SplitInstallSessionStatus.FAILED -> {
                    Log.e("DFM", "Install failed: ${state.errorCode()}")
                }
            }
        }

    private fun openLoginFeature() {
        // 已安装则直接启动
        if (manager.installedModules.contains(splitId)) {
            startActivity(Intent().setClassName(packageName, loginActivity))
            return
        }
        manager.registerListener(listener)
        val req = SplitInstallRequest.newBuilder().addModule(splitId).build()
        manager.startInstall(req)
            .addOnSuccessListener { sid -> sessionId = sid }
            .addOnFailureListener { e -> Log.e("DFM", "startInstall failed", e) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        loader = FeatureLoader(this)


        binding.btnOpenLogin.setOnClickListener {
            openLoginFeature()
//            loader.ensureInstalledAndLaunch(
//                splitId = "login",
//                activityClassName = "com.redwolf.feature_login.LoginActivity"
//            )
        }


        binding.btnPreloadLogin.setOnClickListener {
            loader.deferredInstall(listOf("login"))
        }


        binding.btnUninstallLogin.setOnClickListener {
            loader.deferredUninstall(listOf("login"))
        }
    }


    override fun onResume() {
        super.onResume()
        loader.register()
    }


    override fun onPause() {
        loader.unregister()
        super.onPause()
    }
}