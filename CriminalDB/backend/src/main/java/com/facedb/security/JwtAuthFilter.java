package com.facedb.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        // No JWT -> continue normally.
        // Public endpoints such as /api/auth/login don't need a token.
        if (header == null ||
                !header.startsWith("Bearer ")) {

            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7).trim();

        if (token.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {

            String username =
                    jwtService.extractUsername(token);

            if (username != null &&
                    SecurityContextHolder
                            .getContext()
                            .getAuthentication() == null) {

                if (jwtService.isTokenValid(
                        token,
                        username)) {

                    String role =
                            jwtService.extractRole(token);

                    if (role != null &&
                            !role.isBlank()) {

                        UsernamePasswordAuthenticationToken
                                authentication =
                                new UsernamePasswordAuthenticationToken(
                                        username,
                                        null,
                                        List.of(
                                                new SimpleGrantedAuthority(
                                                        "ROLE_" + role
                                                )
                                        )
                                );

                        SecurityContextHolder
                                .getContext()
                                .setAuthentication(
                                        authentication
                                );
                    }
                }
            }

        } catch (Exception ignored) {

            /*
             * Invalid/expired JWT:
             * do not crash the request.
             *
             * Spring Security will decide whether the endpoint
             * requires authentication.
             */
            SecurityContextHolder
                    .clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
