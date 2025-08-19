package com.redwolf.axix

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.redwolf.axix.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var loader: FeatureLoader


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        loader = FeatureLoader(this)


        binding.btnOpenLogin.setOnClickListener {
            loader.ensureInstalledAndLaunch(
                splitId = "login",
                activityClassName = "com.redwolf.feature_login.LoginActivity"
            )
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