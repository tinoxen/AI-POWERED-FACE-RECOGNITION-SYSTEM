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
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

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

        String username = request.getUsername();

        /*
         * STEP 1: Authenticate username/password
         */
        try {

            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            username,
                            request.getPassword()
                    )
            );

        } catch (BadCredentialsException e) {

            log.warn("Failed login attempt for user: {}", username);

            try {
                auditService.log(
                        username,
                        "LOGIN_FAILED",
                        null,
                        "Bad credentials",
                        httpRequest.getRemoteAddr()
                );
            } catch (RuntimeException auditException) {
                log.error(
                        "Failed to write LOGIN_FAILED audit record for {}",
                        username,
                        auditException
                );
            }

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "INVALID_CREDENTIALS",
                            "message", "Invalid username or password"
                    ));

        } catch (AuthenticationServiceException e) {

            log.error(
                    "Authentication service failed for user {}",
                    username,
                    e
            );

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "AUTHENTICATION_SERVICE_ERROR",
                            "message", "Authentication service is unavailable"
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
                            "error", "AUTHENTICATION_ERROR",
                            "message", "An authentication error occurred"
                    ));
        }

        /*
         * STEP 2: Get the user
         */
        User user;

        try {

            user = userService.getByUsername(username);

            if (user == null) {
                log.error("UserService returned null for username {}", username);

                return ResponseEntity
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of(
                                "error", "USER_NOT_FOUND",
                                "message", "User account could not be loaded"
                        ));
            }

        } catch (RuntimeException e) {

            log.error(
                    "Failed to load user {} after authentication",
                    username,
                    e
            );

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "USER_LOAD_FAILED",
                            "message", "Could not load user information"
                    ));
        }

        /*
         * STEP 3: Generate JWT
         */
        String token;

        try {

            if (user.getRole() == null) {
                log.error("User {} has no role assigned", username);

                return ResponseEntity
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of(
                                "error", "USER_ROLE_MISSING",
                                "message", "User account has no assigned role"
                        ));
            }

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
                            "error", "JWT_GENERATION_FAILED",
                            "message", "Could not create login session"
                    ));
        }

        /*
         * STEP 4: Audit successful login
         *
         * Audit failure must NOT prevent successful authentication.
         */
        try {

            auditService.log(
                    user.getUsername(),
                    "LOGIN",
                    null,
                    null,
                    httpRequest.getRemoteAddr()
            );

        } catch (RuntimeException e) {

            log.error(
                    "Login audit write failed for user {}",
                    user.getUsername(),
                    e
            );
        }

        /*
         * STEP 5: Return successful login response
         */
        return ResponseEntity.ok(
                new LoginResponse(
                        token,
                        user.getUsername(),
                        user.getRole().name()
                )
        );
    }
}
