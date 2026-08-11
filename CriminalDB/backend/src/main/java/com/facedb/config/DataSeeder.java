package com.facedb.config;

import com.facedb.model.User;
import com.facedb.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.boot.CommandLineRunner;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${APP_ADMIN_USERNAME:admin}")
    private String adminUsername;

    @Value("${APP_ADMIN_PASSWORD:}")
    private String adminPassword;

    @Value("${APP_OFFICER_USERNAME:user}")
    private String officerUsername;

    @Value("${APP_OFFICER_PASSWORD:5623}")
    private String officerPassword;

    public DataSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        this.userRepository =
                userRepository;

        this.passwordEncoder =
                passwordEncoder;
    }

    @Override
    public void run(String... args) {
        createAdminIfConfigured();
        createOfficerIfMissing();
    }

    private void createAdminIfConfigured() {

        if (userRepository.findByUsername(
                adminUsername
        ).isPresent()) {

            return;
        }

        if (adminPassword == null ||
                adminPassword.isBlank()) {

            System.out.println(
                    "No APP_ADMIN_PASSWORD configured. " +
                    "Skipping default admin creation."
            );

            return;
        }

        User admin =
                new User(
                        adminUsername,
                        passwordEncoder.encode(
                                adminPassword
                        ),
                        User.Role.ADMIN
                );

        admin.setEnabled(true);

        userRepository.save(admin);

        System.out.println(
                "Created initial admin account: " +
                        adminUsername
        );
    }

    private void createOfficerIfMissing() {
        if (userRepository.findByUsername(officerUsername).isPresent()) {
            return;
        }

        User officer = new User(
                officerUsername,
                passwordEncoder.encode(officerPassword),
                User.Role.OFFICER
        );
        officer.setEnabled(true);
        userRepository.save(officer);

        System.out.println("Created initial officer account: " + officerUsername);
    }
}
