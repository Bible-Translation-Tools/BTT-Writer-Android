package org.unfoldingword.door43client.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a language that a resource will be translated into
 */
@Serializable
data class TargetLanguage(
    @SerialName("id")
    val slug: String,
    val name: String,
    val direction: String,
    @SerialName("anglicized_name")
    val anglicizedName: String = "",
    val region: String = "",
    @SerialName("is_gateway_language")
    val isGatewayLanguage: Boolean = false
)