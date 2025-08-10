package com.redwolf.axix

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.redwolf.plugin_api.PluginActivity

class LocalHelloActivity : PluginActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(hostActivity).apply { text = "Hello from __host__ LocalHelloActivity" }
        hostActivity.setContentView(tv)
    }
}