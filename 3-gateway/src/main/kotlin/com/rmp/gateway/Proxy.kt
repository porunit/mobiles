package com.rmp.gateway

import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.uri
import io.ktor.server.response.respondBytes
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.rmp.gateway.Proxy")

/** Request headers a proxy must not forward verbatim (Content-Type IS forwarded). */
private val REQUEST_HOP = setOf(
    "host", "content-length", "transfer-encoding", "connection", "keep-alive",
    "upgrade", "te", "trailer", "proxy-authenticate", "proxy-authorization",
    "accept-encoding",
)

/** Response headers the proxy sets itself / must not copy. */
private val RESPONSE_HOP = REQUEST_HOP + setOf("content-type", "date", "server")

private val BODY_METHODS = setOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch, HttpMethod.Delete)

/**
 * Transparently forwards the call to db-service: same method, path+query, headers
 * (incl. Authorization + Content-Type) and streamed body; relays db-service's
 * status, content-type and body.
 */
suspend fun proxyRequest(call: ApplicationCall, client: HttpClient, baseUrl: String) {
    val method = call.request.httpMethod
    val target = baseUrl.trimEnd('/') + call.request.uri
    log.debug("proxy {} {} -> {}", method.value, call.request.uri, target)

    val upstream = client.request(target) {
        this.method = method
        headers {
            call.request.headers.forEach { name, values ->
                if (name.lowercase() !in REQUEST_HOP) values.forEach { append(name, it) }
            }
        }
        if (method in BODY_METHODS) setBody(call.receiveChannel())
    }

    val respBytes = upstream.readBytes()
    val contentType = upstream.headers[HttpHeaders.ContentType]
        ?.let { runCatching { ContentType.parse(it) }.getOrNull() }
        ?: ContentType.Application.Json

    upstream.headers.forEach { name, values ->
        if (name.lowercase() !in RESPONSE_HOP) values.forEach { call.response.headers.append(name, it) }
    }
    call.respondBytes(respBytes, contentType, upstream.status)
}
