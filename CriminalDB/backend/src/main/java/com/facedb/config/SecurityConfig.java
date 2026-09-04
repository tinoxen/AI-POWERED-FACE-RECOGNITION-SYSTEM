package com.facedb.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.facedb.security.JwtAuthFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {

        return config.getAuthenticationManager();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider();

        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);

        return provider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
            .cors(cors ->
                cors.configurationSource(corsConfigurationSource())
            )

            .csrf(csrf ->
                csrf.disable()
            )

            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(contentType -> {})
                .referrerPolicy(referrer -> referrer
                    .policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
            )

            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )

            .authorizeHttpRequests(auth -> auth

                // ==============================
                // PUBLIC FRONTEND
                // ==============================

                .requestMatchers(
                    "/",
                    "/index.html",
                    "/login.html",
                    "/dashboard.html",
                    "/view-persons.html",
                    "/add-person.html",
                    "/edit-person.html",
                    "/face-search.html",
                    "/audit-logs.html",

                    "/css/**",
                    "/js/**",
                    "/images/**",
                    "/assets/**"
                ).permitAll()

                // ==============================
                // PUBLIC API
                // ==============================

                .requestMatchers(
                    "/api/auth/**",
                    "/api/health"
                ).permitAll()

                // ==============================
                // PERSON API
                // ==============================

                .requestMatchers(HttpMethod.GET, "/api/persons/**")
                .hasAnyRole("ADMIN", "OFFICER", "VIEWER")

                .requestMatchers(HttpMethod.POST, "/api/persons", "/api/persons/match")
                .hasAnyRole("ADMIN", "OFFICER")

                .requestMatchers(
                    "/api/persons/**"
                ).hasAnyRole("ADMIN", "OFFICER")

                // ==============================
                // AUDIT LOGS
                // ==============================

                .requestMatchers(
                    "/api/audit/**"
                ).hasRole("ADMIN")

                // ==============================
                // EVERYTHING ELSE
                // ==============================

                .anyRequest().authenticated()
            )

            .addFilterBefore(
                jwtAuthFilter,
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration =
                new CorsConfiguration();

        String allowedOrigins =
                System.getenv("ALLOWED_ORIGINS");

        if (allowedOrigins == null ||
                allowedOrigins.isBlank()) {

            configuration.setAllowedOriginPatterns(
                List.of(
                    "http://localhost:5500",
                    "http://127.0.0.1:5500",
                    "http://*:5500"
                )
            );

        } else {

            configuration.setAllowedOriginPatterns(
                Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList()
            );
        }

        configuration.setAllowedMethods(
            List.of(
                "GET",
                "POST",
                "PUT",
                "DELETE",
                "OPTIONS"
            )
        );

        configuration.setAllowedHeaders(
            List.of("*")
        );

        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
            "/**",
            configuration
        );

        return source;
    }
}
