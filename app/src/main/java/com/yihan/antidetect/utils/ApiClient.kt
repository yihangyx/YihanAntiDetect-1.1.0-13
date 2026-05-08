package com.yihan.antidetect.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * API client for authentication
 */
object ApiClient {
    
    private const val AUTH_SERVER = "yh521.cc.cd"
    private const val AUTH_PATH = "/api/verify"
    
    /**
     * Verify key code with the server
     */
    suspend fun verifyKeyCode(keyCode: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://$AUTH_SERVER$AUTH_PATH")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.doOutput = true
            
            val payload = JSONObject().apply {
                put("key_code", keyCode)
            }
            
            connection.outputStream.use { os ->
                val input = payload.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }
            
            val responseCode = connection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                parseAuthResponse(response)
            } else {
                AuthResult(success = false, message = "服务器响应错误: $responseCode")
            }
        } catch (e: Exception) {
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
     */
    suspend fun fetchAnnouncement(): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://$AUTH_SERVER/api/announcement")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 10000
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
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
            null
        }
    }
}

data class AuthResult(
    val success: Boolean,
    val message: String
)