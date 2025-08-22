package com.redwolf.axix

import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import com.redwolf.plugin_api.core.PluginActivity

class LocalHelloStep2Activity : PluginActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val composeView = ComposeView(hostActivity).apply {
            // 方式 A：绑定到宿主生命周期（推荐）
            val owner = hostActivity as? LifecycleOwner
            if (owner != null) {
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnLifecycleDestroyed(owner.lifecycle)
                )
            } else {
                // 方式 B：不依赖宿主强转，挂到 ViewTree（也可用）
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                )
            }

            setContent { PluginScreen2(onClick = {
                this@LocalHelloStep2Activity.performBackPressed()
//                hostActivity.onBackPressedDispatcher.onBackPressed()
            }) }
        }

        hostActivity.setContentView(composeView)
    }
}

@Composable
private fun PluginScreen2(onClick:()-> Unit) {
    MaterialTheme {
        Surface {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                TextButton (content = {
                    Text("回到back")
                }, onClick = {
                    onClick.invoke()
                })
            }
        }
    }
}