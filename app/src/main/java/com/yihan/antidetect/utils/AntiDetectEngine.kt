package com.yihan.antidetect.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Core anti-detection engine
 * Implements the functionality from the original shell script
 */
object AntiDetectEngine {
    
    private val PHONE_MODELS = listOf(
        Triple("Xiaomi", "MI 11", "Xiaomi"),
        Triple("OPPO", "OPPO A96", "OPPO"),
        Triple("vivo", "vivo X80", "vivo"),
        Triple("Samsung", "Galaxy S22", "samsung"),
        Triple("HUAWEI", "HUAWEI P40", "HUAWEI"),
        Triple("Realme", "Realme GT", "realme"),
        Triple("OnePlus", "OnePlus 9", "OnePlus"),
        Triple("Honor", "Honor 50", "Honor")
    )
    
    private val DEVICE_CODES = mapOf(
        "MI 11" to "venus",
        "OPPO A96" to "PEQM00",
        "vivo X80" to "V2180A",
        "Galaxy S22" to "o1s",
        "HUAWEI P40" to "ELS",
        "Realme GT" to "RMX2202",
        "OnePlus 9" to "lemonadep",
        "Honor 50" to "ELZ"
    )
    
    /**
     * Generate a random IMEI number
     */
    fun generateRandomIMEI(): String {
        val tac = "86${String.format("%013d", System.currentTimeMillis() % 10000000000000)}"
        var sum = 0
        for (i in tac.indices) {
            var digit = tac[i].digitToInt()
            if (i % 2 == 1) {
                digit *= 2
                if (digit >= 10) digit -= 9
            }
            sum += digit
        }
        val check = (10 - (sum % 10)) % 10
        return "$tac$check"
    }
    
    /**
     * Generate a random MAC address
     */
    fun generateRandomMAC(): String {
        return String.format(
            "02:%02X:%02X:%02X:%02X:%02X",
            Random.nextInt(256),
            Random.nextInt(256),
            Random.nextInt(256),
            Random.nextInt(256),
            Random.nextInt(256)
        )
    }
    
    /**
     * Generate random hex string
     */
    fun generateRandomHex(length: Int = 16): String {
        return (1..length).map { 
            Random.nextInt(16).toString(16) 
        }.joinToString("")
    }
    
    /**
     * Select a random phone model to spoof
     */
    fun selectRandomPhone(): Triple<String, String, String> {
        return PHONE_MODELS.random()
    }
    
    /**
     * Get device code for a model
     */
    fun getDeviceCode(model: String, hardware: String = "qcom"): Pair<String, String> {
        val device = DEVICE_CODES[model] ?: model.lowercase().replace(" ", "")
        val hw = if (model.contains("HUAWEI", ignoreCase = true)) "kirin" else "qcom"
        return device to hw
    }
    
    /**
     * Get random Android version info
     */
    fun getRandomAndroidVersion(): Pair<Int, String> {
        val sdk = Random.nextInt(4) + 30 // SDK 30-33
        val version = when (sdk) {
            30 -> "11"
            31, 32 -> "12"
            33 -> "13"
            else -> "12"
        }
        return sdk to version
    }
    
    /**
     * Get random carrier info
     */
    fun getRandomCarrier(): Triple<String, String, String> {
        return when (Random.nextInt(3)) {
            0 -> Triple("中国移动", "46000", "cn")
            1 -> Triple("中国联通", "46001", "cn")
            else -> Triple("中国电信", "46003", "cn")
        }
    }
    
    /**
     * Generate spoofed device configuration
     */
    fun generateSpoofConfig(): SpoofConfig {
        val (brand, model, manufacturer) = selectRandomPhone()
        val (device, hardware) = getDeviceCode(model)
        val (sdk, androidVersion) = getRandomAndroidVersion()
        val (carrier, mcc, _) = getRandomCarrier()
        
        val buildId = "SP1A.210812.016"
        val securityPatch = "2025-06-01"
        val fingerprint = "$brand/$device/$device:$androidVersion/$buildId/${buildId.take(8)}:user/release-keys"
        
        return SpoofConfig(
            brand = brand,
            model = model,
            manufacturer = manufacturer,
            device = device,
            hardware = hardware,
            serialNo = generateRandomHex(16),
            imei = generateRandomIMEI(),
            mac = generateRandomMAC(),
            androidVersion = androidVersion,
            sdk = sdk,
            fingerprint = fingerprint,
            securityPatch = securityPatch,
            carrier = carrier,
            mcc = mcc
        )
    }
    
    /**
     * Apply runtime spoofing (setprop commands)
     */
    suspend fun applyRuntimeSpoof(config: SpoofConfig): List<SpoofResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SpoofResult>()
        
        // Carrier settings
        val carrierProps = listOf(
            "gsm.operator.numeric" to config.mcc,
            "gsm.operator.alpha" to config.carrier,
            "gsm.sim.operator.numeric" to config.mcc,
            "gsm.sim.operator.alpha" to config.carrier,
            "gsm.sim.state" to "READY",
            "gsm.sim.operator.iso-country" to "cn",
            "gsm.operator.iso-country" to "cn",
            "gsm.network.type" to "LTE"
        )
        
        for ((key, value) in carrierProps) {
            val success = ShellExecutor.setProp(key, value)
            results.add(SpoofResult(key, value, success))
        }
        
        // Battery settings
        val batteryProps = listOf(
            "sys.battery.level" to Random.nextInt(40) + 50,
            "sys.battery.present" to "true",
            "sys.battery.health" to "good",
            "sys.battery.temperature" to Random.nextInt(150) + 250,
            "sys.battery.technology" to "Li-poly"
        )
        
        for ((key, value) in batteryProps) {
            val success = ShellExecutor.setProp(key, value.toString())
            results.add(SpoofResult(key, value.toString(), success))
        }
        
        // Network settings
        val networkProps = listOf(
            "wifi.interface" to "wlan0",
            "bluetooth.enabled" to "true",
            "bt.enabled" to "true"
        )
        
        for ((key, value) in networkProps) {
            val success = ShellExecutor.setProp(key, value)
            results.add(SpoofResult(key, value, success))
        }
        
        // Security settings
        ShellExecutor.executeRoot("settings put global adb_enabled 0")
        ShellExecutor.executeRoot("settings put global development_settings_enabled 0")
        ShellExecutor.executeRoot("settings put global mock_location 0")
        ShellExecutor.executeRoot("settings put global airplane_mode_on 0")
        ShellExecutor.executeRoot("setprop persist.sys.usb.config mtp")
        ShellExecutor.executeRoot("setprop sys.usb.config mtp")
        ShellExecutor.executeRoot("setprop sys.usb.state mtp")
        
        results
    }
    
    /**
     * Clean emulator traces
     */
    suspend fun cleanEmulatorTraces(): List<String> = withContext(Dispatchers.IO) {
        val cleaned = mutableListOf<String>()
        
        val filesToRemove = listOf(
            "/dev/goldfish_pipe",
            "/dev/qemu_pipe",
            "/system/lib/libc_malloc_debug_qemu.so",
            "/system/lib64/libc_malloc_debug_qemu.so",
            "/data/media/0/baiqiang.log",
            "/data/local/tmp/baiq_pipe"
        )
        
        for (file in filesToRemove) {
            val result = ShellExecutor.executeRoot("rm -f $file")
            if (result.success) {
                cleaned.add("已清理: $file")
            }
        }
        
        // Clear logcat
        ShellExecutor.executeRoot("logcat -c")
        cleaned.add("已清理日志")
        
        cleaned
    }
    
    /**
     * Get current device info
     */
    suspend fun getCurrentDeviceInfo(): DeviceInfo = withContext(Dispatchers.IO) {
        DeviceInfo(
            model = ShellExecutor.getProp("ro.product.model"),
            brand = ShellExecutor.getProp("ro.product.brand"),
            manufacturer = ShellExecutor.getProp("ro.product.manufacturer"),
            device = ShellExecutor.getProp("ro.product.device"),
            hardware = ShellExecutor.getProp("ro.hardware"),
            board = ShellExecutor.getProp("ro.product.board"),
            serialNo = ShellExecutor.getProp("ro.serialno"),
            fingerprint = ShellExecutor.getProp("ro.build.fingerprint"),
            androidVersion = ShellExecutor.getProp("ro.build.version.release"),
            sdkVersion = ShellExecutor.getProp("ro.build.version.sdk"),
            securityPatch = ShellExecutor.getProp("ro.build.security_patch"),
            operator = ShellExecutor.getProp("gsm.operator.alpha"),
            simState = ShellExecutor.getProp("gsm.sim.state"),
            networkType = ShellExecutor.getProp("gsm.network.type")
        )
    }
}

data class SpoofConfig(
    val brand: String,
    val model: String,
    val manufacturer: String,
    val device: String,
    val hardware: String,
    val serialNo: String,
    val imei: String,
    val mac: String,
    val androidVersion: String,
    val sdk: Int,
    val fingerprint: String,
    val securityPatch: String,
    val carrier: String,
    val mcc: String
)

data class SpoofResult(
    val property: String,
    val value: String,
    val success: Boolean
)
