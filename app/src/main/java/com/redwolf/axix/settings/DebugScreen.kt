package com.redwolf.axix.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


@Composable
fun DebugScreen(padding: PaddingValues) {
    Column(Modifier.padding(padding).padding(16.dp)) {
        Text("Debug", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text("字节预算使用（示例）见日志/内部状态；可扩展为实时图表。")
        Text("你可以在此添加：in‑flight 请求数、缓存命中率、吞吐、首屏时间等指标可视化。")
    }
}