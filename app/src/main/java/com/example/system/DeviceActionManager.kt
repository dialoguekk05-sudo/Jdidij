package com.example.system

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class InstalledAppShortcut(
    val label: String,
    val packageName: String
)

data class SystemTelemetry(
    val isOnline: Boolean = true,
    val availableRamMb: Long = 0L,
    val totalRamMb: Long = 0L,
    val isLowRamState: Boolean = false,
    val batteryPercent: Int = 100
)

data class PermissionSnapshot(
    val micGranted: Boolean = false,
    val callPhoneGranted: Boolean = false,
    val notificationsGranted: Boolean = true,
    val accessibilityEnabled: Boolean = false
)

class DeviceActionManager(private val context: Context) {

    private val connectivityManager: ConnectivityManager? =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(checkCurrentConnectivity())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = true
        }

        override fun onLost(network: Network) {
            _isOnline.value = checkCurrentConnectivity()
        }
    }

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, networkCallback)
        } catch (_: Throwable) {
            _isOnline.value = checkCurrentConnectivity()
        }
    }

    fun unregister() {
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (_: Throwable) {
        }
    }

    private fun checkCurrentConnectivity(): Boolean = runCatching {
        val cm = connectivityManager ?: return@runCatching false
        val network = cm.activeNetwork ?: return@runCatching false
        val caps = cm.getNetworkCapabilities(network) ?: return@runCatching false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(false)

    fun readTelemetry(): SystemTelemetry = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val availMb = memInfo.availMem / (1024L * 1024L)
        val totalMb = memInfo.totalMem / (1024L * 1024L)

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val bmCapacity = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val pct = if (bmCapacity in 0..100) {
            bmCapacity
        } else {
            val batteryIntent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) ?: 100
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            if (scale > 0) (level * 100) / scale else 100
        }

        SystemTelemetry(
            isOnline = _isOnline.value,
            availableRamMb = availMb,
            totalRamMb = totalMb,
            isLowRamState = memInfo.lowMemory || totalMb <= 3200L,
            batteryPercent = pct
        )
    }.getOrDefault(SystemTelemetry(isOnline = _isOnline.value))

    fun readPermissions(): PermissionSnapshot = runCatching {
        val mic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val call = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        PermissionSnapshot(
            micGranted = mic,
            callPhoneGranted = call,
            notificationsGranted = notif,
            accessibilityEnabled = JarvisAccessibilityService.isConnected.value
        )
    }.getOrDefault(PermissionSnapshot())

    fun getInstalledApps(): List<InstalledAppShortcut> = runCatching {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                mainIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }
        resolveInfos
            .asSequence()
            .filter { it.activityInfo != null && it.activityInfo.packageName != context.packageName }
            .map { info ->
                InstalledAppShortcut(
                    label = info.loadLabel(pm).toString(),
                    packageName = info.activityInfo.packageName
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }.getOrDefault(emptyList())

    fun findBestMatchingApp(query: String): InstalledAppShortcut? {
        val apps = getInstalledApps()
        val normalized = normalizeAppAlias(query)
        return apps.firstOrNull { it.label.equals(normalized, ignoreCase = true) }
            ?: apps.firstOrNull { it.label.contains(normalized, ignoreCase = true) }
            ?: apps.firstOrNull { normalized.contains(it.label, ignoreCase = true) }
            ?: apps.firstOrNull { it.packageName.contains(normalized.replace(" ", ""), ignoreCase = true) }
    }

    fun launchApp(packageName: String): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(launchIntent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openUrlInBrowser(url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Launches phone dialer or places call only after explicit user confirmation.
     */
    fun executeConfirmedCall(phoneOrQuery: String, preferDirectCall: Boolean): Boolean {
        val sanitized = phoneOrQuery.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        val hasDirectPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val action = if (preferDirectCall && hasDirectPermission && sanitized.isNotBlank()) {
            Intent.ACTION_CALL
        } else {
            Intent.ACTION_DIAL
        }
        val uri = if (sanitized.isNotBlank()) {
            Uri.parse("tel:$sanitized")
        } else {
            Uri.parse("tel:")
        }
        return try {
            val intent = Intent(action, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Opens SMS composer pre-filled with recipient and message after user confirmation.
     */
    fun executeConfirmedSms(recipient: String, body: String): Boolean {
        val sanitized = recipient.filter { it.isDigit() || it == '+' }
        val uri = Uri.parse("smsto:${sanitized}")
        return try {
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                putExtra("sms_body", body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openAppDetailsSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun normalizeAppAlias(raw: String): String {
        val lower = raw.trim().lowercase()
        return when {
            lower.contains("यूट्यूब") || lower.contains("youtube") -> "YouTube"
            lower.contains("व्हाट्सएप") || lower.contains("whatsapp") -> "WhatsApp"
            lower.contains("क्रोम") || lower.contains("chrome") -> "Chrome"
            lower.contains("कैमरा") || lower.contains("camera") -> "Camera"
            lower.contains("सेटिंग") || lower.contains("settings") -> "Settings"
            lower.contains("मैप्स") || lower.contains("maps") -> "Maps"
            lower.contains("जीमेल") || lower.contains("gmail") -> "Gmail"
            lower.contains("फोन") || lower.contains("dialer") || lower.contains("phone") -> "Phone"
            lower.contains("मैसेज") || lower.contains("messages") -> "Messages"
            lower.contains("कैलकुलेटर") || lower.contains("calculator") -> "Calculator"
            lower.contains("घड़ी") || lower.contains("clock") -> "Clock"
            else -> raw.trim()
        }
    }
}
