package com.yihan.antidetect.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yihan.antidetect.utils.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Main ViewModel for the app
 */
class MainViewModel : ViewModel() {
    
    // UI State
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    // Device Info
    private val _deviceInfo = MutableStateFlow(DeviceInfo())
    val deviceInfo: StateFlow<DeviceInfo> = _deviceInfo.asStateFlow()
    
    // Spoof Config
    private val _spoofConfig = MutableStateFlow<SpoofConfig?>(null)
    val spoofConfig: StateFlow<SpoofConfig?> = _spoofConfig.asStateFlow()
    
    // Auth State
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()
    
    // Has Root
    private val _hasRoot = MutableStateFlow<Boolean?>(null)
    val hasRoot: StateFlow<Boolean?> = _hasRoot.asStateFlow()
    
    // Logs
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()
    
    // Announcement
    private val _announcement = MutableStateFlow<String?>(null)
    val announcement: StateFlow<String?> = _announcement.asStateFlow()
    
    init {
        checkRoot()
        fetchAnnouncement()
    }
    
    private fun addLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        _logs.value = _logs.value + "[$timestamp] $message"
    }
    
    fun checkRoot() {
        viewModelScope.launch {
            _uiState.value = UiState.CheckingRoot
            val hasRoot = ShellExecutor.checkRoot()
            _hasRoot.value = hasRoot
            if (hasRoot) {
                addLog("✓ Root权限检测通过")
                loadDeviceInfo()
            } else {
                addLog("✗ 未检测到Root权限")
                _uiState.value = UiState.NoRoot
            }
        }
    }
    
    private fun loadDeviceInfo() {
        viewModelScope.launch {
            _uiState.value = UiState.LoadingInfo
            val info = AntiDetectEngine.getCurrentDeviceInfo()
            _deviceInfo.value = info
            addLog("✓ 设备信息已加载")
            
            // Check for suspicious characteristics
            val warnings = info.hasSuspiciousCharacteristics()
            if (warnings.isNotEmpty()) {
                warnings.forEach { addLog("⚠ $it") }
            } else {
                addLog("✓ 未检测到明显异常特征")
            }
            
            _uiState.value = UiState.Idle
        }
    }
    
    fun fetchAnnouncement() {
        viewModelScope.launch {
            _announcement.value = ApiClient.fetchAnnouncement()
        }
    }
    
    fun authenticate(keyCode: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = UiState.Authenticating
            addLog("正在验证卡密...")
            
            val result = ApiClient.verifyKeyCode(keyCode)
            if (result.success) {
                _isAuthenticated.value = true
                addLog("✓ ${result.message}")
                _uiState.value = UiState.Authenticated
                onSuccess()
            } else {
                addLog("✗ ${result.message}")
                _uiState.value = UiState.AuthFailed(result.message)
            }
        }
    }
    
    fun generateSpoofConfig() {
        val config = AntiDetectEngine.generateSpoofConfig()
        _spoofConfig.value = config
        addLog("已生成伪装配置:")
        addLog("  品牌: ${config.brand}")
        addLog("  型号: ${config.model}")
        addLog("  设备: ${config.device}")
        addLog("  Android: ${config.androidVersion}")
    }
    
    fun applyFullSpoof(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            _uiState.value = UiState.Spoofing
            
            // Generate config if not set
            if (_spoofConfig.value == null) {
                generateSpoofConfig()
            }
            
            val config = _spoofConfig.value ?: run {
                _uiState.value = UiState.Error("配置生成失败")
                onComplete(false)
                return@launch
            }
            
            addLog("开始应用伪装...")
            
            // Apply runtime spoofing
            val results = AntiDetectEngine.applyRuntimeSpoof(config)
            val successCount = results.count { it.success }
            addLog("运行时伪装: $successCount/${results.size} 成功")
            
            // Clean emulator traces
            val cleaned = AntiDetectEngine.cleanEmulatorTraces()
            cleaned.forEach { addLog(it) }
            
            addLog("✓ 伪装应用完成")
            addLog("⚠ 部分属性修改需要重启后生效")
            
            // Reload device info
            loadDeviceInfo()
            
            _uiState.value = UiState.SpoofComplete
            onComplete(true)
        }
    }
    
    fun applyRuntimeOnly() {
        viewModelScope.launch {
            _uiState.value = UiState.Spoofing
            
            val config = AntiDetectEngine.generateSpoofConfig()
            _spoofConfig.value = config
            
            addLog("开始运行时伪装...")
            
            val results = AntiDetectEngine.applyRuntimeSpoof(config)
            val successCount = results.count { it.success }
            addLog("✓ 运行时伪装完成: $successCount/${results.size}")
            
            // Clean traces
            AntiDetectEngine.cleanEmulatorTraces()
            
            loadDeviceInfo()
            _uiState.value = UiState.Idle
        }
    }
    
    fun reboot() {
        viewModelScope.launch {
            addLog("正在重启设备...")
            ShellExecutor.reboot()
        }
    }
    
    fun refreshDeviceInfo() {
        viewModelScope.launch {
            loadDeviceInfo()
        }
    }
    
    fun clearLogs() {
        _logs.value = emptyList()
    }
}

sealed class UiState {
    object Idle : UiState()
    object CheckingRoot : UiState()
    object NoRoot : UiState()
    object LoadingInfo : UiState()
    object Authenticating : UiState()
    object Authenticated : UiState()
    data class AuthFailed(val message: String) : UiState()
    object Spoofing : UiState()
    object SpoofComplete : UiState()
    data class Error(val message: String) : UiState()
}
