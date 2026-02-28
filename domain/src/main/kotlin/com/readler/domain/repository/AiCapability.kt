package com.readler.domain.repository

data class AiCapability(
    val enabled: Boolean = false,
    val hasApiKey: Boolean = false,
    val hasNetwork: Boolean = false
) {
    val canRun: Boolean
        get() = enabled && hasApiKey && hasNetwork
}
