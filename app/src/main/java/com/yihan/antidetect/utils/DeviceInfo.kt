package com.yihan.antidetect.utils

/**
 * Device information data class
 */
data class DeviceInfo(
    val model: String = "",
    val brand: String = "",
    val manufacturer: String = "",
    val device: String = "",
    val hardware: String = "",
    val board: String = "",
    val serialNo: String = "",
    val fingerprint: String = "",
    val androidVersion: String = "",
    val sdkVersion: String = "",
    val securityPatch: String = "",
    val operator: String = "",
    val simState: String = "",
    val networkType: String = ""
) {
    /**
     * Check if device has suspicious characteristics (cloud phone/emulator indicators)
     */
    fun hasSuspiciousCharacteristics(): List<String> {
        val warnings = mutableListOf<String>()
        
        // Check hardware indicators
        if (hardware.contains("goldfish", ignoreCase = true) ||
            hardware.contains("vbox", ignoreCase = true) ||
            hardware.contains("qemu", ignoreCase = true) ||
            hardware.contains("rk3588", ignoreCase = true) ||
            hardware.contains("rk30board", ignoreCase = true) ||
            hardware.contains("rockchip", ignoreCase = true)) {
            warnings.add("硬件特征可疑: $hardware")
        }
        
        // Check model indicators
        if (model.contains("sdk", ignoreCase = true) ||
            model.contains("emulator", ignoreCase = true) ||
            model.contains("generic", ignoreCase = true) ||
            model.contains("rk3588", ignoreCase = true)) {
            warnings.add("型号特征可疑: $model")
        }
        
        // Check board indicators
        if (board.contains("rk35", ignoreCase = true) ||
            board.contains("rockchip", ignoreCase = true)) {
            warnings.add("主板特征可疑: $board")
        }
        
        // Check fingerprint indicators
        if (fingerprint.contains("rk3588", ignoreCase = true) ||
            fingerprint.contains("rockchip", ignoreCase = true) ||
            fingerprint.contains("rpdroid", ignoreCase = true)) {
            warnings.add("指纹含云手机特征")
        }
        
        return warnings
    }
    
    /**
     * Format device info for display
     */
    fun toDisplayList(): List<Pair<String, String>> {
        return listOf(
            "型号" to model,
            "品牌" to brand,
            "制造商" to manufacturer,
            "设备代号" to device,
            "硬件" to hardware,
            "主板" to board,
            "序列号" to serialNo,
            "Android版本" to androidVersion,
            "SDK版本" to sdkVersion,
            "安全补丁" to securityPatch,
            "运营商" to operator,
            "SIM状态" to simState,
            "网络类型" to networkType
        ).filter { it.second.isNotBlank() }
    }
}
