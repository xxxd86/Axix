package com.redwolf.plugin_render_advanced
import android.os.Bundle
import android.widget.TextView
import com.redwolf.plugin_api.core.PluginActivity

class PostTinyActivity : PluginActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(hostActivity)
        tv.text = "Hello from Tiny: render-advanced (Kotlin, no-MVI)!"
        hostActivity.setContentView(tv)
    }
}