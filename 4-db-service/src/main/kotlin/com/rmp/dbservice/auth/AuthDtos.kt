package com.rmp.dbservice.auth

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:Email val email: String,
    @field:Size(min = 6, max = 100) val password: String,
)

data class LoginRequest(
    @field:NotBlank val email: String,
    @field:NotBlank val password: String,
)

data class AuthResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val userId: Long,
    val email: String,
)
