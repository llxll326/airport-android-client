package com.airport.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airport.app.data.entity.ProfileEntity
import kotlinx.coroutines.launch

/** 首页：连接控制卡片 + 节点列表（按订阅分组） */
@Composable
fun ProfilesScreen(
    onGoToSubscriptions: () -> Unit,
    viewModel: ProfilesViewModel = viewModel(),
) {
    val subscriptions by viewModel.subscriptions.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val selectedId by viewModel.selectedProfileId.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val currentProfileId by viewModel.currentProfileId.collectAsState()

    val selectedProfile = profiles.firstOrNull { it.id == selectedId }
    val context = LocalContext.current

    // 连接错误/提示：Snackbar 展示
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val message by viewModel.message.collectAsState()
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    val coreError by viewModel.coreError.collectAsState()
    LaunchedEffect(coreError) {
        coreError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearCoreError()
        }
    }

    Box {
        Column(modifier = Modifier.fillMaxSize()) {
            ConnectionCard(
                isRunning = isRunning,
                connectedProfile = profiles.firstOrNull { it.id == currentProfileId } ?: selectedProfile,
                selectedName = selectedProfile?.name,
                onToggle = viewModel::toggleConnection,
                modifier = Modifier.padding(16.dp),
            )

            if (profiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("还没有可用节点", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onGoToSubscriptions) {
                            Text("去添加订阅 →")
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    subscriptions.forEach { sub ->
                        val subProfiles = profiles.filter { it.subscriptionId == sub.id }
                        if (subProfiles.isNotEmpty()) {
                            item(key = "sub-${sub.id}") {
                                Text(
                                    text = sub.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                                )
                            }
                            items(subProfiles, key = { it.id }) { profile ->
                                ProfileItem(
                                    profile = profile,
                                    selected = profile.id == selectedId,
                                    onClick = { viewModel.onProfileClick(profile) },
                                    onCopy = {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                                as android.content.ClipboardManager
                                        clipboard.setPrimaryClip(
                                            android.content.ClipData.newPlainText(
                                                "节点链接",
                                                profile.link,
                                            ),
                                        )
                                        scope.launch { snackbarHostState.showSnackbar("节点链接已复制") }
                                    },
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 56.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ConnectionCard(
    isRunning: Boolean,
    connectedProfile: ProfileEntity?,
    selectedName: String?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isRunning) "● 已连接" else "未连接",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isRunning) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = connectedProfile?.name ?: selectedName ?: "未选择节点",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = isRunning,
                onCheckedChange = { onToggle() },
            )
        }
    }
}

@Composable
private fun ProfileItem(
    profile: ProfileEntity,
    selected: Boolean,
    onClick: () -> Unit,
    onCopy: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle
                else Icons.Filled.RadioButtonUnchecked,
                contentDescription = if (selected) "已选择" else "未选择",
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${profile.protocol} · ${profile.server}:${profile.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onCopy) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "复制节点链接",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
