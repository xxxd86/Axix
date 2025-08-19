package com.redwolf.axix

import android.app.Application
import android.content.Context
import com.google.android.play.core.splitcompat.SplitCompat


class App : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
// 为老设备/融合APK场景提供 split 兼容，现代设备也可安全调用
        SplitCompat.install(this)
    }
}