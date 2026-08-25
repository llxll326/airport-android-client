package com.airport.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.airport.app.core.CoreController
import com.airport.app.ui.MainScreen
import com.airport.app.ui.theme.AirportTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 拒绝则无通知，不影响使用 */ }

    private val vpnAuthLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            CoreController.onVpnAuthResult(
                this,
                result.resultCode == RESULT_OK,
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 13+ 通知权限（前台服务通知需要）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // 监听 VPN 授权请求
        lifecycleScope.launch {
            CoreController.vpnAuthRequired.collect {
                val intent = VpnService.prepare(this@MainActivity)
                if (intent != null) {
                    vpnAuthLauncher.launch(intent)
                }
            }
        }

        setContent {
            AirportTheme {
                FirstLaunchDialog()
                MainScreen()
            }
        }
    }
}

/** 首次进入应用时展示的欢迎弹窗（仅一次） */
@Composable
private fun FirstLaunchDialog() {
    val context = LocalContext.current
    val app = context.applicationContext as AirportApp
    val welcomeShown by app.settings.welcomeShown.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(welcomeShown) {
        if (!welcomeShown) showDialog = true
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { /* 必须点击按钮关闭 */ },
            title = {
                Text("管哥温馨提示", style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Text("管哥温馨提示，录得越少身体越好")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    scope.launch { app.settings.setWelcomeShown() }
                }) {
                    Text("知道了")
                }
            },
        )
    }
}
