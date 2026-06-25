package com.rmp.dbservice.security

import org.springframework.security.oauth2.jwt.Jwt

/** The authenticated user's id, taken from the JWT subject claim. */
fun Jwt.userId(): Long = subject.toLong()
