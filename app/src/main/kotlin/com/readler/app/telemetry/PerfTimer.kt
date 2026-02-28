package com.readler.app.telemetry

import android.os.SystemClock

class PerfTimer {
    private val startElapsedMs = SystemClock.elapsedRealtime()

    fun elapsedMs(): Long = SystemClock.elapsedRealtime() - startElapsedMs
}
