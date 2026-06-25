package com.rmp.dbservice.auth

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val auth: AuthService) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody req: RegisterRequest): AuthResponse =
        auth.register(req.email, req.password)

    @PostMapping("/login")
    fun login(@Valid @RequestBody req: LoginRequest): AuthResponse =
        auth.login(req.email, req.password)
}
