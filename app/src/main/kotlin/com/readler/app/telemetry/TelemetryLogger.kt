package com.readler.app.telemetry

interface TelemetryLogger {
    fun logEvent(name: String, attributes: Map<String, String> = emptyMap())
    fun logMetric(name: String, valueMs: Long, attributes: Map<String, String> = emptyMap())
    fun logError(name: String, throwable: Throwable?, attributes: Map<String, String> = emptyMap())
}
