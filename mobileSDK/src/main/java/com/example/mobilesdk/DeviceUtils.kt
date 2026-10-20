package com.c4f.mobileSDK

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import android.location.Location
import android.location.LocationManager
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat

object DeviceUtils {

    fun getDeviceInfo(context: Context): Map<String, String> {
        val deviceInfo = mutableMapOf(
            "deviceID" to getDeviceId(context),
            "deviceModel" to Build.MODEL,
            "platform" to "Android",
            "osVersion" to Build.VERSION.RELEASE,
            "manufacturer" to Build.MANUFACTURER,
            "brand" to Build.BRAND,
            "device" to Build.DEVICE,
            "product" to Build.PRODUCT,
            "sdkVersion" to Build.VERSION.SDK_INT.toString(),
            "screenResolution" to getScreenResolution(context),
            "networkType" to getNetworkType(context),
            "timezone" to java.util.TimeZone.getDefault().id,
            "locale" to java.util.Locale.getDefault().toString(),
            "installTime" to getInstallTime(context),
            "appVersion" to getAppVersion(context),
            "deviceOrientation" to getDeviceOrientation(context)
        )
        
        // Add security info
        deviceInfo.putAll(getSecurityInfo(context))
        
        // Add storage info
        deviceInfo.putAll(getStorageInfo(context))
        
        // Add memory info
        deviceInfo.putAll(getMemoryInfo(context))
        
        return deviceInfo
    }

    private fun getDeviceId(context: Context): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun getScreenResolution(context: Context): String {
        val displayMetrics = context.resources.displayMetrics
        return "${displayMetrics.widthPixels}x${displayMetrics.heightPixels}"
    }

    private fun getNetworkType(context: Context): String {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.typeName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun getInstallTime(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.firstInstallTime.toString()
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun getDeviceOrientation(context: Context): String {
        return when (context.resources.configuration.orientation) {
            android.content.res.Configuration.ORIENTATION_LANDSCAPE -> "landscape"
            android.content.res.Configuration.ORIENTATION_PORTRAIT -> "portrait"
            else -> "unknown"
        }
    }

    private fun getSecurityInfo(context: Context): Map<String, String> {
        return try {
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            mapOf(
                "is_secure_screen" to keyguardManager.isKeyguardSecure.toString(),
                "is_encrypted" to (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) "encrypted" else "unknown"),
                "screen_lock" to Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCK_PATTERN_ENABLED, 0).toString()
            )
        } catch (e: Exception) {
            Log.w("DeviceUtils", "Error getting security info: ${e.message}")
            emptyMap()
        }
    }

    private fun getStorageInfo(context: Context): Map<String, String> {
        return try {
            val stat = StatFs(context.filesDir.path)
            val totalBytes = stat.blockCountLong * stat.blockSizeLong
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            
            mapOf(
                "total_storage_mb" to (totalBytes / (1024 * 1024)).toString(),
                "available_storage_mb" to (availableBytes / (1024 * 1024)).toString(),
                "storage_usage_percent" to (((totalBytes - availableBytes) * 100 / totalBytes)).toString()
            )
        } catch (e: Exception) {
            Log.w("DeviceUtils", "Error getting storage info: ${e.message}")
            emptyMap()
        }
    }

    private fun getMemoryInfo(context: Context): Map<String, String> {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            
            val runtime = Runtime.getRuntime()
            val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            val maxMemory = runtime.maxMemory() / (1024 * 1024)
            
            mapOf(
                "total_memory_mb" to (memoryInfo.totalMem / (1024 * 1024)).toString(),
                "available_memory_mb" to (memoryInfo.availMem / (1024 * 1024)).toString(),
                "low_memory" to memoryInfo.lowMemory.toString(),
                "app_used_memory_mb" to usedMemory.toString(),
                "app_max_memory_mb" to maxMemory.toString()
            )
        } catch (e: Exception) {
            Log.w("DeviceUtils", "Error getting memory info: ${e.message}")
            emptyMap()
        }
    }

    fun getLocationData(context: Context): Map<String, String> {
        return try {
            // Check location permissions using ContextCompat for better compatibility
            val hasFineLocation = ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            
            val hasCoarseLocation = ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasFineLocation && !hasCoarseLocation) {
                return mapOf("location_permission" to "denied")
            }

            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            // Try to get last known location from different providers
            var bestLocation: Location? = null
            var bestProvider: String = "unknown"

            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            for (provider in providers) {
                try {
                    if (isLocationProviderEnabled(locationManager, provider)) {
                        val location = locationManager.getLastKnownLocation(provider)
                        if (location != null && (bestLocation == null ||
                                    location.accuracy > bestLocation!!.accuracy)) {
                            bestLocation = location
                            bestProvider = provider
                        }
                    }
                } catch (e: SecurityException) {
                    Log.w("DeviceUtils", "Location permission denied for provider: $provider")
                } catch (e: Exception) {
                    Log.w("DeviceUtils", "Error getting location from $provider: ${e.message}")
                }
            }

            if (bestLocation != null) {
                mapOf(
                    "latitude" to bestLocation.latitude.toString(),
                    "longitude" to bestLocation.longitude.toString(),
                    "location_accuracy" to bestLocation.accuracy.toString(),
                    "location_time" to bestLocation.time.toString(),
                    "location_provider" to bestProvider,
                    "location_available" to "true"
                )
            } else {
                mapOf("location_available" to "false")
            }
        } catch (e: Exception) {
            Log.e("DeviceUtils", "Error getting location: ${e.message}")
            mapOf("location_error" to (e.message ?: "unknown"))
        }
    }

    private fun isLocationProviderEnabled(locationManager: LocationManager, provider: String): Boolean {
        return try {
            locationManager.isProviderEnabled(provider)
        } catch (e: Exception) {
            false
        }
    }

    fun getBatteryInfo(context: Context): Map<String, String> {
        return try {
            val batteryIntent = context.registerReceiver(null,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))

            val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
            val plugged = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            val health = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_HEALTH, -1) ?: -1
            val temperature = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            val voltage = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, -1) ?: -1

            val batteryPercent = if (level != -1 && scale != -1) {
                (level * 100 / scale.toFloat()).toInt().toString()
            } else {
                "unknown"
            }

            val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == android.os.BatteryManager.BATTERY_STATUS_FULL

            val chargeType = when (plugged) {
                android.os.BatteryManager.BATTERY_PLUGGED_AC -> "ac"
                android.os.BatteryManager.BATTERY_PLUGGED_USB -> "usb"
                android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
                else -> "none"
            }

            val batteryHealth = when (health) {
                android.os.BatteryManager.BATTERY_HEALTH_GOOD -> "good"
                android.os.BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
                android.os.BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
                android.os.BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
                android.os.BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "failure"
                android.os.BatteryManager.BATTERY_HEALTH_COLD -> "cold"
                else -> "unknown"
            }

            val batteryTemperature = if (temperature != -1) {
                (temperature / 10.0).toString() // Temperature is in tenths of a degree Celsius
            } else {
                "unknown"
            }

            val batteryVoltage = if (voltage != -1) {
                (voltage / 1000.0).toString() // Convert mV to V
            } else {
                "unknown"
            }

            mapOf(
                "battery_level" to batteryPercent,
                "is_charging" to isCharging.toString(),
                "charge_type" to chargeType,
                "battery_health" to batteryHealth,
                "battery_temperature" to batteryTemperature,
                "battery_voltage" to batteryVoltage
            )
        } catch (e: Exception) {
            Log.e("DeviceUtils", "Error getting battery info: ${e.message}")
            emptyMap()
        }
    }

    // Helper method to get all device data in one call
    fun getAllDeviceData(context: Context): Map<String, Any> {
        return mapOf(
            "device_info" to getDeviceInfo(context),
            "location_data" to getLocationData(context),
            "battery_info" to getBatteryInfo(context),
            "timestamp" to System.currentTimeMillis()
        )
    }
}