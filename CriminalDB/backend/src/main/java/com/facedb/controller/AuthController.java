package com.facedb.controller;

import com.facedb.dto.LoginRequest;
import com.facedb.dto.LoginResponse;
import com.facedb.model.User;
import com.facedb.security.JwtService;
import com.facedb.service.AuditService;
import com.facedb.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log =
            LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtService jwtService;
    private final AuditService auditService;

    public AuthController(
            AuthenticationManager authenticationManager,
            UserService userService,
            JwtService jwtService,
            AuditService auditService) {

        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        String username =
                request.getUsername() == null
                        ? ""
                        : request.getUsername().trim();

        String password = request.getPassword();

        // Validate request
        if (username.isBlank()
                || password == null
                || password.isBlank()) {

            return ResponseEntity
                    .badRequest()
                    .body(Map.of(
                            "error",
                            "INVALID_REQUEST",
                            "message",
                            "Username and password are required"
                    ));
        }

        /*
         * =====================================================
         * 1. AUTHENTICATE USERNAME + PASSWORD
         * =====================================================
         */
        try {

            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            username,
                            password
                    )
            );

        } catch (BadCredentialsException e) {

            log.warn(
                    "Invalid credentials for user {}",
                    username
            );

            writeAuditSafely(
                    username,
                    "LOGIN_FAILED",
                    "Bad credentials",
                    httpRequest
            );

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error",
                            "INVALID_CREDENTIALS",
                            "message",
                            "Invalid username or password"
                    ));

        } catch (AuthenticationException e) {

            log.error(
                    "Authentication failed for user {}",
                    username,
                    e
            );

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error",
                            "AUTHENTICATION_ERROR",
                            "message",
                            "Authentication service could not complete the request"
                    ));

        } catch (RuntimeException e) {

            log.error(
                    "Unexpected authentication error for user {}",
                    username,
                    e
            );

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error",
                            "AUTHENTICATION_ERROR",
                            "message",
                            "An unexpected authentication error occurred"
                    ));
        }

        /*
         * =====================================================
         * 2. LOAD USER
         * =====================================================
         */
        User user;

        try {

            user = userService.getByUsername(username);

        } catch (UsernameNotFoundException e) {

            log.error(
                    "Authenticated user could not be loaded: {}",
                    username,
                    e
            );

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error",
                            "USER_LOAD_FAILED",
                            "message",
                            "Authenticated user could not be loaded"
                    ));

        } catch (RuntimeException e) {

            log.error(
                    "Database/user loading error for {}",
                    username,
                    e
            );

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error",
                            "USER_LOAD_FAILED",
                            "message",
                            "Could not load user information"
                    ));
        }

        if (user == null) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error",
                            "USER_NOT_FOUND",
                            "message",
                            "User account could not be loaded"
                    ));
        }

        /*
         * =====================================================
         * 3. CHECK USER ROLE
         * =====================================================
         */
        if (user.getRole() == null) {

            log.error(
                    "User {} has no role",
                    username
            );

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error",
                            "USER_ROLE_MISSING",
                            "message",
                            "User account has no assigned role"
                    ));
        }

        /*
         * =====================================================
         * 4. GENERATE JWT
         * =====================================================
         */
        String token;

        try {

            token = jwtService.generateToken(
                    user.getUsername(),
                    user.getRole().name()
            );

        } catch (RuntimeException e) {

            log.error(
                    "JWT generation failed for user {}",
                    username,
                    e
            );

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error",
                            "JWT_GENERATION_FAILED",
                            "message",
                            "Could not create login session"
                    ));
        }

        /*
         * =====================================================
         * 5. WRITE AUDIT LOG
         *
         * Audit failure must NOT prevent successful login.
         * =====================================================
         */
        writeAuditSafely(
                user.getUsername(),
                "LOGIN",
                null,
                httpRequest
        );

        /*
         * =====================================================
         * 6. RETURN LOGIN RESPONSE
         * =====================================================
         */
        return ResponseEntity.ok(
                new LoginResponse(
                        token,
                        user.getUsername(),
                        user.getRole().name()
                )
        );
    }

    /*
     * =========================================================
     * SAFE AUDIT LOGGING
     * =========================================================
     */
    private void writeAuditSafely(
            String username,
            String action,
            String details,
            HttpServletRequest request) {

        try {

            auditService.log(
                    username,
                    action,
                    null,
                    details,
                    request.getRemoteAddr()
            );

        } catch (RuntimeException e) {

            log.error(
                    "Audit logging failed for user {} action {}",
                    username,
                    action,
                    e
            );
        }
    }
}
