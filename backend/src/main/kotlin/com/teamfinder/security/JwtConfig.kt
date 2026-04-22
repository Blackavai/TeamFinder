package com.teamfinder.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.teamfinder.config.SecurityConfig

class JwtConfig(config: SecurityConfig) {
    private val algorithm = Algorithm.HMAC256(config.secret)
    val realm = config.secret
    val issuer = config.issuer

    val verifier: JWTVerifier = JWT
        .require(algorithm)
        .withIssuer(issuer)
        .build()

    fun generateToken(userId: String): String = JWT.create()
        .withSubject("Authentication")
        .withIssuer(issuer)
        .withClaim("userId", userId.toInt())
        .sign(algorithm)
}