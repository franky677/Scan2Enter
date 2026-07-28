package com.scan2enter.api

/**
 * Ubicazione restituita dal Scan2Enter Gateway.
 *
 * Endpoint:
 * GET /api/locations
 * GET /api/product/{articleId}/locations
 */
data class LocationDto(
    val id: Int,
    val name: String
)