package com.readler.app

import android.app.Application

class ReadlerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.initialize(this)

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppContainer.telemetryLogger.logError(
                name = "uncaught_exception",
                throwable = throwable,
                attributes = mapOf("thread" to thread.name)
            )
            previousHandler?.uncaughtException(thread, throwable)
        }
    }
}
