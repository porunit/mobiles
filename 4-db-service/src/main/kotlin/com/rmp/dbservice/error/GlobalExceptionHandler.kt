package com.rmp.dbservice.error

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

/** Unified error body: { timestamp, status, error, code, message, path, traceId }. */
data class ApiError(
    val timestamp: String,
    val status: Int,
    val error: String,
    val code: String,
    val message: String,
    val path: String,
    val traceId: String?,
)

@RestControllerAdvice
class GlobalExceptionHandler {

    private fun body(status: HttpStatus, code: String, message: String, req: HttpServletRequest) =
        ResponseEntity.status(status).body(
            ApiError(
                timestamp = Instant.now().toString(),
                status = status.value(),
                error = status.reasonPhrase,
                code = code,
                message = message,
                path = req.requestURI,
                traceId = MDC.get("trace_id"),
            ),
        )

    @ExceptionHandler(ConflictException::class)
    fun conflict(e: ConflictException, req: HttpServletRequest) =
        body(HttpStatus.CONFLICT, "conflict", e.message ?: "conflict", req)

    @ExceptionHandler(UnauthorizedException::class)
    fun unauthorized(e: UnauthorizedException, req: HttpServletRequest) =
        body(HttpStatus.UNAUTHORIZED, "unauthorized", e.message ?: "unauthorized", req)

    @ExceptionHandler(NotFoundException::class)
    fun notFound(e: NotFoundException, req: HttpServletRequest) =
        body(HttpStatus.NOT_FOUND, "not_found", e.message ?: "not found", req)

    @ExceptionHandler(BadRequestException::class)
    fun badRequest(e: BadRequestException, req: HttpServletRequest) =
        body(HttpStatus.BAD_REQUEST, "bad_request", e.message ?: "bad request", req)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(e: MethodArgumentNotValidException, req: HttpServletRequest): ResponseEntity<ApiError> {
        val msg = e.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return body(HttpStatus.BAD_REQUEST, "validation_error", msg.ifBlank { "validation failed" }, req)
    }
}
