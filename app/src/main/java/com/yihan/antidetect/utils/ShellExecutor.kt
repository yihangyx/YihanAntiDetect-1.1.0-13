package com.yihan.antidetect.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shell command executor for root operations
 */
object ShellExecutor {
    
    /**
     * Execute a shell command with root privileges
     */
    suspend fun executeRoot(command: String): ShellResult = withContext(Dispatchers.IO) {
        execute("su -c \"$command\"")
    }
    
    /**
     * Execute a shell command without root
     */
    suspend fun execute(command: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(command)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            
            val output = StringBuilder()
            val error = StringBuilder()
            
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            
            while (errorReader.readLine().also { line = it } != null) {
                error.append(line).append("\n")
            }
            
            val exitCode = process.waitFor()
            
            ShellResult(
                success = exitCode == 0,
                output = output.toString().trim(),
                error = error.toString().trim(),
                exitCode = exitCode
            )
        } catch (e: Exception) {
            ShellResult(
                success = false,
                output = "",
                error = e.message ?: "Unknown error",
                exitCode = -1
            )
        }
    }
    
    /**
     * Check if device has root access
     */
    suspend fun checkRoot(): Boolean {
        val result = execute("su -c id")
        return result.success && result.output.contains("uid=0")
    }
    
    /**
     * Get a system property
     */
    suspend fun getProp(key: String): String {
        val result = execute("getprop $key")
        return result.output.trim()
    }
    
    /**
     * Set a system property (requires root)
     */
    suspend fun setProp(key: String, value: String): Boolean {
        val result = executeRoot("setprop $key $value")
        return result.success
    }
    
    /**
     * Reboot the device (requires root)
     */
    suspend fun reboot(): Boolean {
        val result = executeRoot("reboot")
        return result.success
    }
}

data class ShellResult(
    val success: Boolean,
    val output: String,
    val error: String,
    val exitCode: Int
)
