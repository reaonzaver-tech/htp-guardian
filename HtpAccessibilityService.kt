package com.HTPGuardian.myapplication

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL

class HtpAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    private var lastHandshakeTime = 0L
    private val DEBOUNCE_MS = 1500L
    private var lastFocusedSource: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Dynamisch alle Focus-Events anfordern – robuster als nur XML-Config
        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_FOCUSED or
                          AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        info.notificationTimeout = 100
        serviceInfo = info

        HtpLogger.log("🟢 AccessibilityService AKTIV – bereit für Focus-Events")
        Log.d("HTP", "AccessibilityService verbunden")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Alle Focus-Events loggen damit wir sehen was ankommt
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            val className = event.className?.toString() ?: ""
            HtpLogger.log("👁 Focus: $className")

            // Breiter Check – EditText, Input, TextField, etc.
            val isInputField = listOf("EditText", "Input", "TextField", "AutoComplete")
                .any { className.contains(it, ignoreCase = true) }

            if (!isInputField) return

            val viewId = event.source?.viewIdResourceName ?: "unknown"
            val sourceId = "${event.packageName}::${className}::${viewId}"
            val now = System.currentTimeMillis()

            if (sourceId == lastFocusedSource && (now - lastHandshakeTime) < DEBOUNCE_MS) return

            lastFocusedSource = sourceId
            lastHandshakeTime = now

            HtpLogger.log("⌨️ Eingabefeld erkannt: ${event.packageName}")
            HtpLogger.log("🔐 Starte Handshake...")
            executeHandshake()
        }
    }

    private fun executeHandshake() {
        val notarUrls = listOf(
            "http://10.0.3.2:4000/sign",
            "http://10.0.3.2:4001/sign",
            "http://10.0.3.2:4002/sign"
        )
        val dummyData = (1..32).map { (Math.random() * 256).toInt() }
        val jsonBody = """{"data":[${dummyData.joinToString(",")}]}"""

        serviceScope.launch {
            var successCount = 0
            notarUrls.forEachIndexed { index, urlString ->
                val port = 4000 + index
                val label = "Notar ${index + 1} (:$port)"
                try {
                    val conn = URL(urlString).openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true
                    conn.connectTimeout = 2000
                    conn.readTimeout = 2000
                    conn.outputStream.use { it.write(jsonBody.toByteArray()) }
                    val code = conn.responseCode
                    conn.disconnect()
                    if (code == 200) {
                        successCount++
                        HtpLogger.log("✅ $label – OK")
                    } else {
                        HtpLogger.log("⚠️ $label – HTTP $code")
                    }
                } catch (e: Exception) {
                    HtpLogger.log("❌ $label – ${e.javaClass.simpleName}")
                }
            }
            HtpLogger.log(if (successCount >= 2)
                "🟢 QUORUM OK ($successCount/3)"
            else
                "🔴 QUORUM FEHLGESCHLAGEN ($successCount/3)")
        }
    }

    override fun onInterrupt() {
        HtpLogger.log("⚠️ Service unterbrochen!")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
