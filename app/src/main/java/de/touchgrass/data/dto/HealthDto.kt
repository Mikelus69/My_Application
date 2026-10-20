package de.touchgrass.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class HealthDto(val status: String, val database: String)
