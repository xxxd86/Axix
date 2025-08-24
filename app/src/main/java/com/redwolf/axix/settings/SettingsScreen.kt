package com.redwolf.axix.settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.redwolf.mvi_dynamic_plugin.traffic.AppSettingsState
import com.redwolf.mvi_dynamic_plugin.traffic.NetType


@Composable
fun SettingsScreen(padding: PaddingValues) {
    var saver by remember { mutableStateOf(AppSettingsState.dataSaver) }
    var net by remember { mutableStateOf(NetType.WIFI) }
    var battery by remember { mutableStateOf(80) }
    var charging by remember { mutableStateOf(true) }


    Column(Modifier.padding(padding).padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))


        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("省流模式")
            Switch(checked = saver, onCheckedChange = { saver = it; AppSettingsState.dataSaver = it })
        }


        Spacer(Modifier.height(8.dp))
        Text("网络类型：$net")
        Row { NetType.values().forEach { t ->
            AssistChip(onClick = { net = t }, label = { Text(t.name) }, modifier = Modifier.padding(end=8.dp))
        } }


        Spacer(Modifier.height(8.dp))
        Text("电量：$battery%")
        Slider(value = battery/100f, onValueChange = { battery = (it*100).toInt() })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("充电中")
            Switch(checked = charging, onCheckedChange = { charging = it })
        }


        Text("提示：本示例中的信号仅示意，未真实联动 EdgeSignals（为保持示例轻量）。")
    }
}