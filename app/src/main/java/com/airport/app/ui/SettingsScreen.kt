package com.airport.app.ui

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.airport.app.BuildConfig
import com.airport.app.AirportApp
import java.io.File

/** 设置页（MVP：关于信息 + 运行日志） */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var showLogDialog by remember { mutableStateOf(false) }
    var logContent by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingRow("应用版本", BuildConfig.VERSION_NAME)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingRow("核心引擎", "sing-box")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingRow("设备", "${Build.MANUFACTURER} ${Build.MODEL}")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            TextButton(
                onClick = {
                    val file = File(context.filesDir, "sing-box.log")
                    logContent = if (file.exists()) file.readText().takeLast(4000)
                    else "（暂无日志，连接一次 VPN 后生成）"
                    showLogDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("查看运行日志")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "说明：\n· 添加订阅后，节点会自动解析入库\n· 连接会创建 VPN 隧道，系统会弹出授权提示\n· 切换节点时若已连接将自动重连\n· 连接异常时，请到「查看运行日志」获取 sing-box 日志辅助排查",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showLogDialog) {
        AlertDialog(
            onDismissRequest = { showLogDialog = false },
            title = { Text("运行日志") },
            text = {
                Text(
                    logContent,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .height(400.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = { showLogDialog = false }) { Text("关闭") }
            },
        )
    }
}

@Composable
private fun SettingRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
