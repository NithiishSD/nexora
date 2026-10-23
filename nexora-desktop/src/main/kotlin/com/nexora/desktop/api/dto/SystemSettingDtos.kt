package com.nexora.desktop.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class SetupStatusResponse(
    val initialized: Boolean,
    val businessName: String? = null,
)

@Serializable
data class InitializeSetupRequest(
    val username: String,
    val password: String,
    val businessName: String? = null,
    val phone: String? = null,
    val city: String? = null,
)

@Serializable
data class UpdateSettingsRequest(
    val settings: Map<String, String>,
)
