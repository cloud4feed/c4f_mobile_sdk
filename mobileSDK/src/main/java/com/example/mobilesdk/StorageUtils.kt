package com.c4f.mobileSDK

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.util.Log
import java.io.File

object StorageUtils {

    private val userKeys = listOf(
        "userID", "userId", "user_id", "id",
        "userEmail", "email", "user_email",
        "userName", "username", "user_name",
        "userTier", "user_tier", "tier",
        "userRole", "user_role", "role",
        "isPremium", "is_premium", "premium",
        "isLoggedIn", "is_logged_in", "logged_in",
        "sessionId", "session_id",
        "installSource", "install_source",
        "appVersion", "app_version",
        "language", "locale"
    )

    // KEEPING YOUR EXISTING METHOD
    fun findUserData(context: Context): Map<String, String> {
        val data = mutableMapOf<String, String>()
        
        Log.d("StorageUtils", "🔍 Starting comprehensive user data search...")
        
        // NEW: Scan ALL SharedPreferences files, not just "app_prefs"
        scanAllSharedPreferences(context, data)
        
        // KEEP: Check EncryptedSharedPreferences (your existing logic)
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                "secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            findDataInPreferences(encryptedPrefs, data, "EncryptedSharedPreferences")
        } catch (e: Exception) {
            Log.d("StorageUtils", "⚠️ EncryptedSharedPreferences not available: ${e.message}")
        }
        
        Log.d("StorageUtils", "✅ Found ${data.size} user data items")
        return data
    }

    // NEW: Scan ALL SharedPreferences files in the app
    private fun scanAllSharedPreferences(context: Context, data: MutableMap<String, String>) {
        try {
            val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            
            if (!prefsDir.exists() || !prefsDir.isDirectory) {
                Log.d("StorageUtils", "📁 No shared_prefs directory found")
                return
            }
            
            // List all XML preference files
            prefsDir.listFiles { file -> 
                file.name.endsWith(".xml") && !file.name.contains("device-")
            }?.forEach { prefsFile ->
                try {
                    val prefsName = prefsFile.name.removeSuffix(".xml")
                    
                    // Skip common system/invalid files
                    if (prefsName.contains("com.google") || prefsName.contains("WebView")) {
                        return@forEach
                    }
                    
                    val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    
                    // Check if this file contains any of our target keys
                    var foundKeys = 0
                    userKeys.forEach { key ->
                        if (prefs.contains(key)) {
                            foundKeys++
                        }
                    }
                    
                    if (foundKeys > 0) {
                        Log.d("StorageUtils", "📄 Scanning: $prefsName ($foundKeys potential keys)")
                        findDataInPreferences(prefs, data, "Prefs: $prefsName")
                    }
                } catch (e: Exception) {
                    Log.d("StorageUtils", "⚠️ Could not read $prefsFile: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("StorageUtils", "❌ Error scanning SharedPreferences: ${e.message}")
        }
    }

    // KEEPING YOUR EXISTING METHOD (unchanged)
    private fun findDataInPreferences(prefs: SharedPreferences, data: MutableMap<String, String>, source: String) {
        userKeys.forEach { key ->
            try {
                if (prefs.contains(key)) {
                    val value = prefs.getAll()[key]
                    val stringValue = when (value) {
                        is String -> value
                        is Boolean -> value.toString()
                        is Int -> value.toString()
                        is Long -> value.toString()
                        is Float -> value.toString()
                        else -> null
                    }

                    stringValue?.let {
                        if (it.isNotBlank()) {
                            // Use standardized key names (your existing logic)
                            when {
                                key.contains("id", ignoreCase = true) && !data.containsKey("userId") -> 
                                    data["userId"] = it
                                key.contains("email", ignoreCase = true) && !data.containsKey("userEmail") -> 
                                    data["userEmail"] = it
                                key.contains("name", ignoreCase = true) && !data.containsKey("userName") -> 
                                    data["userName"] = it
                                key.contains("tier", ignoreCase = true) && !data.containsKey("userTier") -> 
                                    data["userTier"] = it
                                key.contains("role", ignoreCase = true) && !data.containsKey("userRole") -> 
                                    data["userRole"] = it
                                key.contains("premium", ignoreCase = true) && !data.containsKey("isPremium") -> 
                                    data["isPremium"] = it
                                key.contains("logged", ignoreCase = true) && !data.containsKey("isLoggedIn") -> 
                                    data["isLoggedIn"] = it
                                key.contains("session", ignoreCase = true) && !data.containsKey("sessionId") -> 
                                    data["sessionId"] = it
                                key.contains("install", ignoreCase = true) && !data.containsKey("installSource") -> 
                                    data["installSource"] = it
                                key.contains("version", ignoreCase = true) && !data.containsKey("appVersion") -> 
                                    data["appVersion"] = it
                                key.contains("language", ignoreCase = true) && !data.containsKey("language") -> 
                                    data["language"] = it
                                else -> if (!data.containsKey(key)) data[key] = it
                            }
                            // Track where we found it
                            data["${key}Source"] = source
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("StorageUtils", "Error reading key $key: ${e.message}")
            }
        }
    }

    // KEEPING YOUR EXISTING METHOD (enhanced for multi-location search)
    fun findSpecificData(context: Context, key: String): String? {
        Log.d("StorageUtils", "🔍 Searching for key: $key")
        
        // First, try your original locations
        val fromOriginal = findInOriginalLocations(context, key)
        if (fromOriginal != null) {
            Log.d("StorageUtils", "✅ Found in original location: $fromOriginal")
            return fromOriginal
        }
        
        // If not found, scan all SharedPreferences files
        val fromAllPrefs = findKeyInAllPreferences(context, key)
        if (fromAllPrefs != null) {
            Log.d("StorageUtils", "✅ Found in scanned prefs: $fromAllPrefs")
            return fromAllPrefs
        }
        
        Log.d("StorageUtils", "❌ Key not found: $key")
        return null
    }
    
    // Helper: Your original location check (app_prefs + secure_prefs)
    private fun findInOriginalLocations(context: Context, key: String): String? {
        // Check regular SharedPreferences first
        val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        if (sharedPrefs.contains(key)) {
            return when {
                sharedPrefs.getAll()[key] is String -> sharedPrefs.getString(key, null)
                sharedPrefs.getAll()[key] is Boolean -> sharedPrefs.getBoolean(key, false).toString()
                sharedPrefs.getAll()[key] is Int -> sharedPrefs.getInt(key, 0).toString()
                sharedPrefs.getAll()[key] is Long -> sharedPrefs.getLong(key, 0L).toString()
                sharedPrefs.getAll()[key] is Float -> sharedPrefs.getFloat(key, 0f).toString()
                else -> null
            }
        } else {
            // Check encrypted if not found
            try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                val encryptedPrefs = EncryptedSharedPreferences.create(
                    context,
                    "secure_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )

                if (encryptedPrefs.contains(key)) {
                    return when {
                        encryptedPrefs.getAll()[key] is String -> encryptedPrefs.getString(key, null)
                        encryptedPrefs.getAll()[key] is Boolean -> encryptedPrefs.getBoolean(key, false).toString()
                        encryptedPrefs.getAll()[key] is Int -> encryptedPrefs.getInt(key, 0).toString()
                        encryptedPrefs.getAll()[key] is Long -> encryptedPrefs.getLong(key, 0L).toString()
                        encryptedPrefs.getAll()[key] is Float -> encryptedPrefs.getFloat(key, 0f).toString()
                        else -> null
                    }
                }
            } catch (e: Exception) {
                // Ignore encryption errors
            }
        }
        return null
    }
    
    // Helper: Search for key in ALL SharedPreferences files
    private fun findKeyInAllPreferences(context: Context, key: String): String? {
        try {
            val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            
            if (!prefsDir.exists() || !prefsDir.isDirectory) {
                return null
            }
            
            prefsDir.listFiles { file -> file.name.endsWith(".xml") }?.forEach { prefsFile ->
                try {
                    val prefsName = prefsFile.name.removeSuffix(".xml")
                    val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    
                    if (prefs.contains(key)) {
                        val value = prefs.getAll()[key]
                        val result = when (value) {
                            is String -> value
                            is Boolean -> value.toString()
                            is Int -> value.toString()
                            is Long -> value.toString()
                            is Float -> value.toString()
                            else -> null
                        }
                        
                        if (result != null) {
                            Log.d("StorageUtils", "📁 Found '$key' in: $prefsName = $result")
                            return result
                        }
                    }
                } catch (e: Exception) {
                    // Skip this file
                }
            }
        } catch (e: Exception) {
            Log.e("StorageUtils", "Error scanning for key: ${e.message}")
        }
        
        return null
    }

    // NEW: Convenience method to find user ID (most common use case)
    fun findUserId(context: Context): String? {
        // Check all variants of user ID keys
        val userIdVariants = listOf("userID", "userId", "user_id", "id", "uid", "userUID", "userUId", "userUid", "user_uid")
        
        userIdVariants.forEach { variant ->
            findSpecificData(context, variant)?.let { 
                Log.d("StorageUtils", "✅ Found user ID via '$variant': $it")
                return it 
            }
        }
        
        // Also check the full user data map
        val userData = findUserData(context)
        userData["userId"]?.let { 
            Log.d("StorageUtils", "✅ Found user ID in full scan: $it")
            return it 
        }
        
        Log.d("StorageUtils", "❌ No user ID found")
        return null
    }
}