package com.rmp.dbservice.user

import com.rmp.dbservice.error.NotFoundException
import com.rmp.dbservice.repo.UserRepository
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserController(private val users: UserRepository) {

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal jwt: Jwt): Map<String, Any?> {
        val userId = jwt.subject.toLong()
        val user = users.findById(userId).orElseThrow { NotFoundException("user not found") }
        return mapOf(
            "id" to user.id,
            "email" to user.email,
            "cashBalance" to user.cashBalance,
            "createdAt" to user.createdAt,
        )
    }
}
