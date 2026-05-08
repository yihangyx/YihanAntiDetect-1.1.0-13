package com.yihan.antidetect.utils

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

/**
 * Crypto utilities for API request signing and response decryption
 * Matches server-side crypto.ts implementation
 */
object CryptoUtils {
    
    // Server-side keys (hardcoded for now - in production these should be obfuscated)
    private const val ENCRYPTION_KEY_HEX = "ecb9a46f815480576b4808bbf80070156bd96fe7b68dd305233b63d2686f3138"
    private const val HMAC_KEY_HEX = "ec772a1ba19da42ccb8a4d93b6aaccd797245a7301cd279c9bdaeba07ad17958"
    
    private val encryptionKey: ByteArray by lazy {
        hexToBytes(ENCRYPTION_KEY_HEX)
    }
    
    private val hmacKey: ByteArray by lazy {
        hexToBytes(HMAC_KEY_HEX)
    }
    
    /**
     * Generate HMAC-SHA256 signature for request payload
     * @param payload JSON payload string
     * @return Base64 encoded signature
     */
    fun signPayload(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(hmacKey, "HmacSHA256")
        mac.init(secretKey)
        val signature = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(signature, Base64.NO_WRAP)
    }
    
    /**
     * Decrypt AES-256-GCM encrypted response from server
     * Server returns: {"v": 1, "enc": {"iv": "base64", "tag": "base64", "data": "base64"}}
     * @param jsonResponse Raw JSON response body from server
     * @return Decrypted plaintext string, or null on failure
     */
    fun decryptResponse(jsonResponse: String): String? {
        return try {
            val json = JSONObject(jsonResponse)
            
            // Check protocol version
            val version = json.optInt("v", 0)
            if (version != 1) {
                android.util.Log.w("CryptoUtils", "Unknown protocol version: $version")
                return null
            }
            
            val enc = json.getJSONObject("enc")
            val ivBase64 = enc.getString("iv")
            val tagBase64 = enc.getString("tag")
            val dataBase64 = enc.getString("data")
            
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val tag = Base64.decode(tagBase64, Base64.NO_WRAP)
            val ciphertext = Base64.decode(dataBase64, Base64.NO_WRAP)
            
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(encryptionKey, "AES")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            
            // Java AES/GCM: prepend auth tag to ciphertext; GCM extracts tagLen bits from front
            val combined = ByteArray(tag.size + ciphertext.size)
            System.arraycopy(tag, 0, combined, 0, tag.size)
            System.arraycopy(ciphertext, 0, combined, tag.size, ciphertext.size)
            
            val decrypted = cipher.doFinal(combined)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Generate device fingerprint for additional security
     * @return SHA-256 hash of device identifiers
     */
    fun generateDeviceFingerprint(): String {
        val sb = StringBuilder()
        
        // Collect various device identifiers
        sb.append(android.os.Build.BOARD)
        sb.append(android.os.Build.BOOTLOADER)
        sb.append(android.os.Build.BRAND)
        sb.append(android.os.Build.DEVICE)
        sb.append(android.os.Build.HARDWARE)
        sb.append(android.os.Build.ID)
        sb.append(android.os.Build.MANUFACTURER)
        sb.append(android.os.Build.MODEL)
        sb.append(android.os.Build.PRODUCT)
        sb.append(android.os.Build.SERIAL ?: "unknown")
        sb.append(android.os.Build.TAGS)
        sb.append(android.os.Build.TYPE)
        sb.append(android.os.Build.USER)
        
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(sb.toString().toByteArray(Charsets.UTF_8))
        return bytesToHex(hash)
    }
    
    /**
     * Generate machine code (for key binding)
     * @return Short hex hash of device fingerprint
     */
    fun generateMachineCode(): String {
        return generateDeviceFingerprint().substring(0, 16)
    }
    
    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) +
                    Character.digit(hex[i + 1], 16)).toByte()
        }
        return data
    }
    
    private fun bytesToHex(bytes: ByteArray): String {
        val hexArray = "0123456789ABCDEF".toCharArray()
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }
}
