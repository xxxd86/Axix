package com.redwolf.axix

import android.app.Application
import com.redwolf.mvi_dynamic_plugin.android.AndroidSignals
import com.redwolf.mvi_dynamic_plugin.core.EdgeKit
import com.redwolf.mvi_dynamic_plugin.traffic.HeuristicPolicy

class AxixApp : Application() {
    override fun onCreate() {
        super.onCreate()
//        EdgeKit.init(
//            policy = HeuristicPolicy(dataSaver = { /* 读你的设置或远程开关 */ false }),
//            signals = AndroidSignals(this)
//        )
    }
}