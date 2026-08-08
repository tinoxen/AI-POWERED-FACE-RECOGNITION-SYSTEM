package com.facedb.controller;

import com.facedb.dto.LoginRequest;
import com.facedb.dto.LoginResponse;
import com.facedb.model.User;
import com.facedb.security.JwtService;
import com.facedb.service.AuditService;
import com.facedb.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtService jwtService;
    private final AuditService auditService;

    public AuthController(AuthenticationManager authenticationManager, UserService userService,
                           JwtService jwtService, AuditService auditService) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        } catch (BadCredentialsException e) {
            auditService.log(request.getUsername(), "LOGIN_FAILED", null, "Bad credentials", httpRequest.getRemoteAddr());
            return ResponseEntity.status(401).body("Invalid username or password");
        }

        try {
            User user = userService.getByUsername(request.getUsername());
            String token = jwtService.generateToken(user.getUsername(), user.getRole().name());

            try {
                auditService.log(user.getUsername(), "LOGIN", null, null, httpRequest.getRemoteAddr());
            } catch (RuntimeException auditFailure) {
                // Authentication has succeeded; an unavailable audit table must not prevent
                // the operator from receiving a valid session. The failure remains visible
                // in server logs for immediate database repair.
                log.error("Login audit write failed for user {}", user.getUsername(), auditFailure);
            }

            return ResponseEntity.ok(new LoginResponse(token, user.getUsername(), user.getRole().name()));
        } catch (RuntimeException processingFailure) {
            log.error("Login session creation failed for user {}", request.getUsername(), processingFailure);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "LOGIN_SESSION_CREATION_FAILED",
                    "message", "The server could not create a login session. Check the server logs."
            ));
        }
    }
}
