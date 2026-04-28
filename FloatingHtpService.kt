package com.HTPGuardian.myapplication

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.util.Base64
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class FloatingHtpService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatView: TextView
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private var initialX = 0; private var initialY = 0
    private var initialTouchX = 0f; private var initialTouchY = 0f
    private var isDragging = false

    data class NotaryTarget(val name: String, val url: String)

    private val notaries = listOf(
        NotaryTarget("Alpha", "http://10.0.3.2:4000/sign"),
        NotaryTarget("Beta",  "http://10.0.3.2:4001/sign"),
        NotaryTarget("Gamma", "http://10.0.3.2:4002/sign")
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        floatView = TextView(this).apply {
            text = "🔐"
            textSize = 22f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#CC0d2b0d"))
            setPadding(20, 20, 20, 20)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 20; y = 300 }

        floatView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    isDragging = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                        isDragging = true
                        params.x = initialX + dx; params.y = initialY + dy
                        windowManager.updateViewLayout(floatView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!isDragging) triggerHandshake(); true }
                else -> false
            }
        }

        windowManager.addView(floatView, params)
        HtpLogger.log("🟢 Floating HTP-Button aktiv")
    }

    private fun buildPayload(): Pair<String, String> {
        // ANDROID_ID als Geräte-Identifikator (funktioniert auf Android 10+)
        val deviceId = Settings.Secure.getString(
            contentResolver, Settings.Secure.ANDROID_ID
        ) ?: UUID.randomUUID().toString()

        val timestamp = System.currentTimeMillis() / 1000
        val nonce     = UUID.randomUUID().toString().replace("-", "").take(16)

        val payloadJson = """{"deviceId":"$deviceId","timestamp":$timestamp,"nonce":"$nonce"}"""

        // base64url ohne Padding (kompatibel mit Go und JS)
        val payloadB64 = Base64.encodeToString(
            payloadJson.toByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return Pair(payloadB64, deviceId)
    }

    private fun triggerHandshake() {
        floatView.text = "⏳"
        floatView.setBackgroundColor(Color.parseColor("#CC1a1a3a"))
        HtpLogger.log("──────────────────────────")

        val (payloadB64, deviceId) = buildPayload()
        HtpLogger.log("🔐 Handshake gestartet")
        HtpLogger.log("📱 Device: ${deviceId.take(12)}...")

        serviceScope.launch {
            var successCount = 0
            val sigParts = mutableListOf<String>()

            notaries.forEach { notary ->
                val result = withContext(Dispatchers.IO) {
                    try {
                        val body = """{"payload":"$payloadB64"}"""
                        val conn = URL(notary.url).openConnection() as HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.setRequestProperty("Content-Type", "application/json")
                        conn.doOutput = true
                        conn.connectTimeout = 2000
                        conn.readTimeout    = 2000
                        conn.outputStream.use { it.write(body.toByteArray()) }
                        val code = conn.responseCode
                        val response = conn.inputStream.bufferedReader().readText()
                        conn.disconnect()
                        if (code == 200) JSONObject(response) else null
                    } catch (e: Exception) { null }
                }

                if (result != null) {
                    successCount++
                    val sig = result.getString("signature")
                    sigParts.add("${notary.name}:$sig")
                    HtpLogger.log("✅ ${notary.name} – signiert")
                } else {
                    HtpLogger.log("❌ ${notary.name} – OFFLINE")
                }
            }

            val ok = successCount >= 2
            floatView.text = if (ok) "✅" else "❌"
            floatView.setBackgroundColor(
                Color.parseColor(if (ok) "#CC003311" else "#CC330000")
            )

            if (ok) {
                val token = "HTP-T3-$payloadB64.${sigParts.joinToString(".")}"
                HtpLogger.log("🟢 QUORUM OK ($successCount/3)")
                HtpLogger.log("🎫 Token: ${token.take(40)}...")
                // Token für die App speichern (z.B. für spätere API-Calls)
                HtpLogger.log("✔ Token bereit für API-Verwendung")
            } else {
                HtpLogger.log("🔴 QUORUM FEHLGESCHLAGEN ($successCount/3)")
            }

            delay(3000)
            floatView.text = "🔐"
            floatView.setBackgroundColor(Color.parseColor("#CC0d2b0d"))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatView.isInitialized) windowManager.removeView(floatView)
        serviceScope.cancel()
    }
}
