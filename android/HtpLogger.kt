package com.HTPGuardian.myapplication

import android.os.Handler
import android.os.Looper

/**
 * Statischer Logger-Singleton.
 * Service schreibt rein, Activity registriert einen Callback.
 * Kein Broadcast nötig – alles im selben Prozess.
 */
object HtpLogger {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var callback: ((String) -> Unit)? = null

    // Puffer für Nachrichten die ankommen bevor die Activity ready ist
    private val buffer = mutableListOf<String>()
    private const val MAX_BUFFER = 200

    fun register(cb: (String) -> Unit) {
        callback = cb
        // Gepufferte Nachrichten sofort nachliefern
        val pending = buffer.toList()
        buffer.clear()
        pending.forEach { cb(it) }
    }

    fun unregister() {
        callback = null
    }

    fun log(msg: String) {
        mainHandler.post {
            val cb = callback
            if (cb != null) {
                cb(msg)
            } else {
                // Activity gerade nicht aktiv – puffern
                if (buffer.size < MAX_BUFFER) buffer.add(msg)
            }
        }
    }
}
