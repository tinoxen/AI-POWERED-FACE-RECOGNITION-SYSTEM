package com.facedb.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms:3600000}")
    private long expirationMs;

    /**
     * Creates the signing key from the JWT secret.
     *
     * The secret must be at least 32 bytes long for HS256.
     */
    private SecretKey key() {

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "APP_JWT_SECRET is not configured"
            );
        }

        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);

        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "APP_JWT_SECRET must be at least 32 bytes long"
            );
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generate JWT token.
     */
    public String generateToken(String username, String role) {

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                    "Username cannot be empty"
            );
        }

        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException(
                    "Role cannot be empty"
            );
        }

        Date now = new Date();
        Date expiry = new Date(
                now.getTime() + expirationMs
        );

        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key())
                .compact();
    }

    /**
     * Extract username from JWT.
     */
    public String extractUsername(String token) {
        return extractClaim(
                token,
                Claims::getSubject
        );
    }

    /**
     * Extract role from JWT.
     */
    public String extractRole(String token) {
        return extractClaim(
                token,
                claims -> claims.get("role", String.class)
        );
    }

    /**
     * Validate JWT.
     */
    public boolean isTokenValid(
            String token,
            String username) {

        try {

            String tokenUsername = extractUsername(token);

            return tokenUsername != null
                    && tokenUsername.equals(username)
                    && !isExpired(token);

        } catch (Exception e) {

            return false;
        }
    }

    /**
     * Check whether JWT has expired.
     */
    private boolean isExpired(String token) {

        Date expiration = extractClaim(
                token,
                Claims::getExpiration
        );

        return expiration == null
                || expiration.before(new Date());
    }

    /**
     * Extract a claim from JWT.
     */
    private <T> T extractClaim(
            String token,
            Function<Claims, T> resolver) {

        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                    "JWT token cannot be empty"
            );
        }

        Claims claims = Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return resolver.apply(claims);
    }
}
