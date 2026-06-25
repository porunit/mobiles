package com.rmp.dbservice.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import java.time.Instant

/** Issues HS256 access tokens with the shared secret (claims per ARCHITECTURE §4). */
@Service
class TokenIssuer(
    private val encoder: JwtEncoder,
    @Value("\${JWT_ISSUER:rmp-trading}") private val issuer: String,
    @Value("\${JWT_AUDIENCE:rmp-clients}") private val audience: String,
    @Value("\${JWT_ACCESS_TTL_SECONDS:86400}") private val ttlSeconds: Long,
) {
    data class IssuedToken(val token: String, val expiresIn: Long)

    fun issue(userId: Long, email: String, roles: List<String> = listOf("USER")): IssuedToken {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .audience(listOf(audience))
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(ttlSeconds))
            .claim("email", email)
            .claim("roles", roles)
            .claim("typ", "access")
            .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        val token = encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
        return IssuedToken(token, ttlSeconds)
    }
}
