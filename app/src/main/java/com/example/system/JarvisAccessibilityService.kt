package com.example.system

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class JarvisGlobalAction(val label: String, val systemActionId: Int) {
    BACK("Back", AccessibilityService.GLOBAL_ACTION_BACK),
    HOME("Home", AccessibilityService.GLOBAL_ACTION_HOME),
    RECENTS("Recents", AccessibilityService.GLOBAL_ACTION_RECENTS),
    NOTIFICATIONS("Notifications", AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS),
    QUICK_SETTINGS("Quick Settings", AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
}

/**
 * Privacy-respecting AccessibilityService.
 * Only executes explicit global navigation commands requested by the user.
 * Never reads screen text or window content (canRetrieveWindowContent = false).
 */
class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally ignored: JARVIS never monitors or collects screen content.
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onDestroy() {
        instance = null
        _isConnected.value = false
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var instance: JarvisAccessibilityService? = null

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        fun performGlobalNavigation(action: JarvisGlobalAction): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(action.systemActionId)
        }
    }
}
