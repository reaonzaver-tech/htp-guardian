package com.HTPGuardian.myapplication

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var logTextView: TextView
    private lateinit var logScrollView: ScrollView
    private lateinit var statusDot: TextView
    private lateinit var statusText: TextView
    private val mainScope = CoroutineScope(Dispatchers.Main + Job())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        logTextView   = findViewById(R.id.logTextView)
        logScrollView = findViewById(R.id.logScrollView)
        statusDot     = findViewById(R.id.statusDot)
        statusText    = findViewById(R.id.statusText)

        findViewById<Button>(R.id.btnTestHandshake).setOnClickListener {
            appendLog("──────────────────────────", "#555555")
            appendLog("🔧 MANUELLER TEST", "#aaaaaa")
            runHandshake()
        }

        findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            logTextView.text = ""
            appendLog("Log geleert.", "#555555")
        }

        findViewById<Button>(R.id.btnFloating).setOnClickListener {
            startFloatingService()
        }
    }

    override fun onResume() {
        super.onResume()
        HtpLogger.register { msg -> renderLog(msg) }
        setStatus("BEREIT", "#ccaa00")
        appendLog("System gestartet", "#555555")
    }

    override fun onPause() {
        super.onPause()
        HtpLogger.unregister()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }

    private fun startFloatingService() {
        if (!Settings.canDrawOverlays(this)) {
            // Permission anfordern
            appendLog("⚠️ Overlay-Permission fehlt – öffne Einstellungen...", "#ffaa00")
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            startService(Intent(this, FloatingHtpService::class.java))
            appendLog("🟢 Floating Button gestartet", "#00cc66")
            setStatus("OVERLAY AKTIV", "#00cc66")
        }
    }

    private fun renderLog(msg: String) {
        val color = when {
            msg.startsWith("✅") || msg.startsWith("🟢") -> "#00cc66"
            msg.startsWith("❌") || msg.startsWith("🔴") -> "#ff4444"
            msg.startsWith("⚠️") || msg.startsWith("⚠")  -> "#ffaa00"
            msg.startsWith("⏳") || msg.startsWith("🔐")  -> "#6699ff"
            msg.startsWith("👁")                         -> "#888888"
            msg.startsWith("──")                         -> "#333333"
            msg.startsWith("🟡")                         -> "#ccaa00"
            else -> "#00ff00"
        }
        when {
            msg.startsWith("🟢 QUORUM OK")            -> setStatus("QUORUM OK", "#00cc66")
            msg.startsWith("🔴 QUORUM")               -> setStatus("FEHLGESCHLAGEN", "#ff4444")
            msg.startsWith("🔐")                      -> setStatus("HANDSHAKE...", "#6699ff")
            msg.startsWith("🟢 AccessibilityService") -> setStatus("SERVICE AKTIV", "#00ff88")
            msg.startsWith("🟢 Floating")             -> setStatus("OVERLAY AKTIV", "#00cc66")
        }
        appendLog(msg, color)
    }

    private fun appendLog(msg: String, color: String) {
        val ts   = timeFormat.format(Date())
        val line = "<font color='#444444'>[$ts]</font> <font color='$color'>$msg</font><br/>"
        logTextView.append(android.text.Html.fromHtml(line, android.text.Html.FROM_HTML_MODE_LEGACY))
        logScrollView.post { logScrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun setStatus(label: String, color: String) {
        statusDot.setTextColor(Color.parseColor(color))
        statusText.text  = label
        statusText.setTextColor(Color.parseColor(color))
    }

    private fun runHandshake() {
        val notarUrls = listOf(
            "http://10.0.3.2:4000/sign",
            "http://10.0.3.2:4001/sign",
            "http://10.0.3.2:4002/sign"
        )
        val dummyData = (1..32).map { (Math.random() * 256).toInt() }
        val jsonBody  = """{"data":[${dummyData.joinToString(",")}]}"""

        mainScope.launch {
            var successCount = 0
            notarUrls.forEachIndexed { index, urlString ->
                val port  = 4000 + index
                val label = "Notar ${index + 1} (:$port)"
                appendLog("⏳ $label – verbinde...", "#6699ff")
                val result = withContext(Dispatchers.IO) {
                    try {
                        val conn = URL(urlString).openConnection() as HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.setRequestProperty("Content-Type", "application/json")
                        conn.doOutput = true
                        conn.connectTimeout = 3000
                        conn.readTimeout    = 3000
                        conn.outputStream.use { it.write(jsonBody.toByteArray()) }
                        val code = conn.responseCode
                        conn.disconnect()
                        "HTTP $code"
                    } catch (e: Exception) { "FEHLER: ${e.javaClass.simpleName}" }
                }
                if (result.startsWith("HTTP 200")) {
                    successCount++
                    appendLog("✅ $label – OK", "#00cc66")
                } else {
                    appendLog("❌ $label – $result", "#ff4444")
                }
            }
            val ok = successCount >= 2
            setStatus(if (ok) "QUORUM OK" else "FEHLGESCHLAGEN", if (ok) "#00cc66" else "#ff4444")
            appendLog(
                if (ok) "🟢 QUORUM OK ($successCount/3) – Mensch bestätigt."
                else    "🔴 QUORUM FEHLGESCHLAGEN ($successCount/3)",
                if (ok) "#00cc66" else "#ff4444"
            )
        }
    }
}
