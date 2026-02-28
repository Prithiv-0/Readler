package com.readler.app.telemetry

import android.content.Context
import java.io.File

class FileTelemetryLogger(
    context: Context
) : TelemetryLogger {

    private val lock = Any()
    private val logFile: File = File(context.filesDir, "telemetry/events.log").apply {
        parentFile?.mkdirs()
        if (!exists()) {
            createNewFile()
        }
    }

    override fun logEvent(name: String, attributes: Map<String, String>) {
        write("event", name, null, attributes)
    }

    override fun logMetric(name: String, valueMs: Long, attributes: Map<String, String>) {
        write("metric", name, null, attributes + ("valueMs" to valueMs.toString()))
    }

    override fun logError(name: String, throwable: Throwable?, attributes: Map<String, String>) {
        val payload = if (throwable == null) {
            attributes
        } else {
            attributes + mapOf(
                "exception" to throwable::class.java.simpleName,
                "message" to (throwable.message ?: "")
            )
        }
        write("error", name, throwable, payload)
    }

    private fun write(type: String, name: String, throwable: Throwable?, attributes: Map<String, String>) {
        val timestamp = System.currentTimeMillis()
        val attrString = attributes.entries.joinToString(separator = ",") { (key, value) ->
            "\"${escape(key)}\":\"${escape(value)}\""
        }
        val stackTrace = throwable?.stackTraceToString()?.let { "\"stack\":\"${escape(it)}\"," } ?: ""

        val line = "{\"ts\":$timestamp,\"type\":\"$type\",\"name\":\"${escape(name)}\",$stackTrace\"attrs\":{$attrString}}"

        synchronized(lock) {
            runCatching {
                logFile.appendText(line)
                logFile.appendText("\n")
            }
        }
    }

    private fun escape(raw: String): String {
        return raw
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}
