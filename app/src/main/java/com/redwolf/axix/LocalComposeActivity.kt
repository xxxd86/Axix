package com.redwolf.axix

import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.redwolf.plugin_api.PluginComposeActivity

class LocalComposeActivity : PluginComposeActivity(){
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    var count by remember { mutableStateOf(0) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Hello from Compose Plugin! count=${'$'}count")
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { count++ }) { Text("+1") }
                    }
                }
            }
        }
    }
}