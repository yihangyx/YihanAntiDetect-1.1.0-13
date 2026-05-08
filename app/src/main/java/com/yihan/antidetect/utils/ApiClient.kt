package com.yihan.antidetect.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * API client for authentication with request signing and response encryption
 */
object ApiClient {
    
    private const val AUTH_SERVER = "yh521.cc.cd"
    private const val AUTH_PATH = "/api/verify"
    
    /**
     * Verify key code with the server
     * Now includes request signing and response decryption
     */
    suspend fun verifyKeyCode(keyCode: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://$AUTH_SERVER$AUTH_PATH")
            val connection = url.openConnection() as HttpURLConnection
            
            // Build request payload
            val payload = JSONObject().apply {
                put("key_code", keyCode)
                put("machine_code", CryptoUtils.generateMachineCode())
            }
            val payloadString = payload.toString()
            
            // Generate signature
            val signature = CryptoUtils.signPayload(payloadString)
            
            // Generate device fingerprint
            val fingerprint = CryptoUtils.generateDeviceFingerprint()
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-API-Signature", signature)
            connection.setRequestProperty("X-Device-Fingerprint", fingerprint)
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.doOutput = true
            
            connection.outputStream.use { os ->
                val input = payloadString.toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }
            
            val responseCode = connection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val encryptedResponse = connection.inputStream.bufferedReader().readText()
                
                // Decrypt the response
                val decryptedJson = CryptoUtils.decryptResponse(encryptedResponse)
                    ?: return@withContext AuthResult(success = false, message = "响应解密失败")
                
                parseAuthResponse(decryptedJson)
            } else {
                val errorStream = connection.errorStream?.bufferedReader()?.readText()
                AuthResult(success = false, message = "服务器响应错误: $responseCode${errorStream?.let { " - $it" } ?: ""}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            AuthResult(success = false, message = "连接失败: ${e.message}")
        }
    }
    
    private fun parseAuthResponse(response: String): AuthResult {
        return try {
            val json = JSONObject(response)
            
            // Check various success indicators
            val success = json.optBoolean("success", false) ||
                    json.optString("status") == "active" ||
                    json.optString("message").contains("成功", ignoreCase = true)
            
            val message = when {
                success -> "验证成功"
                json.has("message") -> json.getString("message")
                json.has("error") -> json.getString("error")
                else -> "验证失败"
            }
            
            AuthResult(success = success, message = message)
        } catch (e: Exception) {
            AuthResult(success = false, message = "响应解析失败")
        }
    }
    
    /**
     * Fetch announcement from server
     * Now includes response decryption
     */
    suspend fun fetchAnnouncement(): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://$AUTH_SERVER/api/announcement")
            val connection = url.openConnection() as HttpURLConnection
            
            // Generate device fingerprint for announcement API too
            val fingerprint = CryptoUtils.generateDeviceFingerprint()
            
            connection.requestMethod = "GET"
            connection.setRequestProperty("X-Device-Fingerprint", fingerprint)
            connection.connectTimeout = 5000
            connection.readTimeout = 10000
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val encryptedResponse = connection.inputStream.bufferedReader().readText()
                
                // Decrypt the response
                val decryptedJson = CryptoUtils.decryptResponse(encryptedResponse)
                    ?: return@withContext null
                
                val json = JSONObject(decryptedJson)
                // API 返回格式: {"success": true, "data": [{"content": "..."}]}
                val dataArray = json.optJSONArray("data")
                var content: String? = null
                if (dataArray != null && dataArray.length() > 0) {
                    content = dataArray.getJSONObject(0).optString("content", null)
                }
                if (content != null) {
                    // 移除标签格式: 【公告】或 公告: 等
                    var result = content
                    result = result.replace(Regex("^[\\u4e00-\\u9fa5]+[:：]"), "")
                    result = result.replace(Regex("^[【\\[][\\u4e00-\\u9fa5\\w]+[】\\]][:：]?"), "")
                    result.trim().ifEmpty { null }
                } else null
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

data class AuthResult(
    val success: Boolean,
    val message: String
)
