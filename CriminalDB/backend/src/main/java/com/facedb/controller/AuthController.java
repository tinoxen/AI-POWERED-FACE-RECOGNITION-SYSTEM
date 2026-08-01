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

@RestController
@RequestMapping("/api/auth")
public class AuthController {

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

        User user = userService.getByUsername(request.getUsername());
        String token = jwtService.generateToken(user.getUsername(), user.getRole().name());

        auditService.log(user.getUsername(), "LOGIN", null, null, httpRequest.getRemoteAddr());

        return ResponseEntity.ok(new LoginResponse(token, user.getUsername(), user.getRole().name()));
    }
}
