package com.nexora.desktop.api.dto

import kotlinx.serialization.Serializable

// Shared shapes of 05-API-SPEC sections 1.7 and 3.2.

/** One page of a list (05 section 1.7). */
@Serializable
data class PageResponse<T>(
    val content: List<T> = emptyList(),
    val page: Int = 0,
    val size: Int = 20,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
)

@Serializable
data class PartyRef(val id: String, val partyCode: String? = null, val name: String)

@Serializable
data class AccountRef(val id: String, val accountName: String, val accountType: String? = null)

/** Body of the actions that need a reason (cancel, reverse). */
@Serializable
data class ReasonRequest(val reason: String)
