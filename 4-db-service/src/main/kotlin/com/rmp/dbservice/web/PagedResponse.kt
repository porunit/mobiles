package com.rmp.dbservice.web

/** Standard pagination envelope: { content, totalElements, totalPages, page, size }. */
data class PagedResponse<T>(
    val content: List<T>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
)
