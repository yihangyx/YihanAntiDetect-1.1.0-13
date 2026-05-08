package com.yihan.antidetect.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yihan.antidetect.utils.DeviceInfo
import com.yihan.antidetect.utils.SpoofConfig
import com.yihan.antidetect.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val spoofConfig by viewModel.spoofConfig.collectAsState()
    val hasRoot by viewModel.hasRoot.collectAsState()
    val isAuthenticated by viewModel.isAuthenticated.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val announcement by viewModel.announcement.collectAsState()
    
    var showAuthDialog by remember { mutableStateOf(false) }
    var showRebootDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    
    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated && selectedTab == 0) {
            selectedTab = 1
        }
    }
    
    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "YH",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                        Surface(
                            color = Primary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "v1.1.0",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                color = Primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    titleContentColor = TextPrimary
                ),
                actions = {
                    IconButton(onClick = { viewModel.refreshDeviceInfo(); viewModel.fetchAnnouncement() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "刷新",
                            tint = TextSecondary
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Announcement banner
            AnimatedVisibility(
                visible = announcement != null,
                enter = fadeIn() + slideInVertically()
            ) {
                announcement?.let { text ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Purple.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Campaign,
                                contentDescription = null,
                                tint = Purple,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text,
                                fontSize = 13.sp,
                                color = Purple,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = { viewModel.fetchAnnouncement() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "刷新公告",
                                    tint = Purple.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            // Root status indicator
            RootStatusBanner(hasRoot = hasRoot)
            
            // Tabs
            if (isAuthenticated) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Surface,
                    contentColor = Primary
                ) {
                    listOf(
                        "认证" to Icons.Default.VpnKey,
                        "伪装" to Icons.Default.Shield,
                        "状态" to Icons.Default.Info,
                        "日志" to Icons.Default.Terminal
                    ).forEachIndexed { index, (title, icon) ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) },
                            icon = { Icon(icon, contentDescription = null) }
                        )
                    }
                }
            }
            
            // Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when {
                    !isAuthenticated -> {
                        AuthScreen(
                            uiState = uiState,
                            onAuth = { key -> 
                                viewModel.authenticate(key) {
                                    selectedTab = 1
                                }
                            }
                        )
                    }
                    selectedTab == 1 -> {
                        SpoofScreen(
                            deviceInfo = deviceInfo,
                            spoofConfig = spoofConfig,
                            uiState = uiState,
                            onFullSpoof = { viewModel.applyFullSpoof { } },
                            onRuntimeSpoof = { viewModel.applyRuntimeOnly() },
                            onGenerateConfig = { viewModel.generateSpoofConfig() },
                            onReboot = { showRebootDialog = true }
                        )
                    }
                    selectedTab == 2 -> {
                        StatusScreen(deviceInfo = deviceInfo)
                    }
                    selectedTab == 3 -> {
                        LogScreen(
                            logs = logs,
                            onClear = { viewModel.clearLogs() }
                        )
                    }
                }
            }
        }
    }
    
    // Reboot confirmation dialog
    if (showRebootDialog) {
        AlertDialog(
            onDismissRequest = { showRebootDialog = false },
            title = { Text("确认重启") },
            text = { Text("设备将立即重启，确定继续吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRebootDialog = false
                        viewModel.reboot()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Error)
                ) {
                    Text("重启")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRebootDialog = false }) {
                    Text("取消")
                }
            },
            containerColor = Surface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }
}

@Composable
fun RootStatusBanner(hasRoot: Boolean?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = when (hasRoot) {
            true -> Success.copy(alpha = 0.1f)
            false -> Error.copy(alpha = 0.1f)
            null -> Surface
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (hasRoot) {
                true -> {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Success,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Root权限已获取",
                        fontSize = 13.sp,
                        color = Success
                    )
                }
                false -> {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = Error,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "未检测到Root权限",
                        fontSize = 13.sp,
                        color = Error
                    )
                }
                null -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Primary,
                        strokeWidth = 2.dp
                    )
                    Text(
                        "检测Root权限中...",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun AuthScreen(
    uiState: UiState,
    onAuth: (String) -> Unit
) {
    var keyCode by remember { mutableStateOf("") }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Logo
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Primary.copy(alpha = 0.1f),
                modifier = Modifier.size(100.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = Primary
                    )
                }
            }
            
            Text(
                "请输入卡密以继续",
                fontSize = 18.sp,
                color = TextSecondary
            )
            
            // Key input
            OutlinedTextField(
                value = keyCode,
                onValueChange = { keyCode = it },
                label = { Text("卡密") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(0.8f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = SurfaceLight,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedLabelColor = Primary,
                    unfocusedLabelColor = TextSecondary,
                    cursorColor = Primary
                ),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = {
                    Icon(Icons.Default.VpnKey, contentDescription = null, tint = TextSecondary)
                }
            )
            
            // Auth button
            Button(
                onClick = { onAuth(keyCode) },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                enabled = keyCode.isNotBlank() && uiState !is UiState.Authenticating
            ) {
                if (uiState is UiState.Authenticating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("验证", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
            
            // Error message
            AnimatedVisibility(
                visible = uiState is UiState.AuthFailed,
                enter = fadeIn() + slideInVertically()
            ) {
                if (uiState is UiState.AuthFailed) {
                    Surface(
                        color = Error.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = Error, modifier = Modifier.size(18.dp))
                            Text(uiState.message, fontSize = 13.sp, color = Error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpoofScreen(
    deviceInfo: DeviceInfo,
    spoofConfig: SpoofConfig?,
    uiState: UiState,
    onFullSpoof: () -> Unit,
    onRuntimeSpoof: () -> Unit,
    onGenerateConfig: () -> Unit,
    onReboot: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Config preview
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = Primary)
                    Text("伪装配置预览", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onGenerateConfig) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("重新生成", fontSize = 12.sp)
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                if (spoofConfig != null) {
                    InfoRow("品牌", spoofConfig.brand, spoofConfig.brand != deviceInfo.brand)
                    InfoRow("型号", spoofConfig.model, spoofConfig.model != deviceInfo.model)
                    InfoRow("制造商", spoofConfig.manufacturer, spoofConfig.manufacturer != deviceInfo.manufacturer)
                    InfoRow("设备代号", spoofConfig.device, spoofConfig.device != deviceInfo.device)
                    InfoRow("Android", spoofConfig.androidVersion, spoofConfig.androidVersion != deviceInfo.androidVersion)
                    InfoRow("运营商", spoofConfig.carrier, spoofConfig.carrier != deviceInfo.operator)
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("点击「重新生成」创建配置", color = TextSecondary, fontSize = 14.sp)
                    }
                }
            }
        }
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Shield,
                label = "一键伪装",
                description = "完整伪装+重启生效",
                color = Primary,
                loading = uiState is UiState.Spoofing,
                onClick = onFullSpoof
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.FlashOn,
                label = "运行时伪装",
                description = "立即生效，无需重启",
                color = Secondary,
                loading = uiState is UiState.Spoofing,
                onClick = onRuntimeSpoof
            )
            
            ActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.RestartAlt,
                label = "重启设备",
                description = "应用永久修改",
                color = Warning,
                loading = false,
                onClick = onReboot
            )
        }
        
        // Warning card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Warning.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Warning, modifier = Modifier.size(20.dp))
                Column {
                    Text("注意事项", fontWeight = FontWeight.SemiBold, color = Warning, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "一键伪装会修改系统属性文件，需要重启后完全生效。运行时伪装立即生效，但部分应用可能仍能检测到原始值。",
                        fontSize = 12.sp,
                        color = Warning.copy(alpha = 0.8f),
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    description: String,
    color: Color,
    loading: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(16.dp),
        onClick = { if (!loading) onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = color,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = color
                )
            }
            Text(label, fontWeight = FontWeight.SemiBold, color = color, fontSize = 14.sp)
            Text(description, fontSize = 11.sp, color = TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
fun StatusScreen(deviceInfo: DeviceInfo) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Device section
        SectionCard("设备信息", Icons.Default.PhoneAndroid) {
            deviceInfo.toDisplayList().forEach { (label, value) ->
                InfoRow(label, value)
            }
        }
        
        // Warnings
        val warnings = deviceInfo.hasSuspiciousCharacteristics()
        if (warnings.isNotEmpty()) {
            SectionCard("检测警告", Icons.Default.Warning, warning = true) {
                warnings.forEach { warning ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = Error, modifier = Modifier.size(16.dp))
                        Text(warning, fontSize = 13.sp, color = Error)
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Success.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Success)
                    Text("未检测到异常特征", color = Success, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    icon: ImageVector,
    warning: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (warning) Error.copy(alpha = 0.05f) else Surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (warning) Error else Primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextPrimary)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, highlighted: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Surface(
            color = if (highlighted) Secondary.copy(alpha = 0.15f) else SurfaceLight,
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                value.ifEmpty { "-" },
                fontSize = 13.sp,
                color = if (highlighted) Secondary else TextPrimary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun LogScreen(
    logs: List<String>,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Terminal, contentDescription = null, tint = Primary)
                Text("运行日志", fontWeight = FontWeight.SemiBold, color = TextPrimary)
            }
            TextButton(onClick = onClear) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("清空", fontSize = 12.sp)
            }
        }
        
        // Logs
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0D1117))
        ) {
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无日志", color = TextSecondary, fontSize = 14.sp)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    logs.forEach { log ->
                        Text(
                            log,
                            fontSize = 12.sp,
                            color = when {
                                log.contains("✓") -> Success
                                log.contains("✗") -> Error
                                log.contains("⚠") -> Warning
                                else -> Color(0xFF8B949E)
                            },
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
