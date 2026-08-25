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
                MainScreen()
            }
        }
    }
}
