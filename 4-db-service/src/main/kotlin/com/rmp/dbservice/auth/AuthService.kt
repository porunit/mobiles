package com.rmp.dbservice.auth

import com.rmp.dbservice.domain.UserAccount
import com.rmp.dbservice.error.ConflictException
import com.rmp.dbservice.error.UnauthorizedException
import com.rmp.dbservice.repo.UserRepository
import com.rmp.dbservice.security.TokenIssuer
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val users: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenIssuer: TokenIssuer,
) {
    @Transactional
    fun register(email: String, password: String): AuthResponse {
        val normalized = email.trim().lowercase()
        if (users.existsByEmail(normalized)) throw ConflictException("email already registered")
        val user = users.save(UserAccount(email = normalized, passwordHash = passwordEncoder.encode(password)))
        return token(user)
    }

    @Transactional(readOnly = true)
    fun login(email: String, password: String): AuthResponse {
        val user = users.findByEmail(email.trim().lowercase()) ?: throw UnauthorizedException("invalid credentials")
        if (!passwordEncoder.matches(password, user.passwordHash)) throw UnauthorizedException("invalid credentials")
        return token(user)
    }

    private fun token(user: UserAccount): AuthResponse {
        val issued = tokenIssuer.issue(user.id, user.email)
        return AuthResponse(issued.token, "Bearer", issued.expiresIn, user.id, user.email)
    }
}
