package com.redwolf.plugin_render_advanced

import android.os.Bundle
import android.widget.TextView
import com.redwolf.plugin_api.PluginActivity

class PostEnhancedActivity : PluginActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(hostActivity)
        tv.text = "Hello from Plugin: render-advanced (Kotlin, no-MVI)!"
        hostActivity.setContentView(tv)
    }
}