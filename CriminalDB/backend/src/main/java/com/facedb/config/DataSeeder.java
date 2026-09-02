package com.facedb.config;

import com.facedb.model.User;
import com.facedb.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.boot.CommandLineRunner;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${APP_ADMIN_USERNAME:admin}")
    private String adminUsername;

    @Value("${APP_ADMIN_PASSWORD:}")
    private String adminPassword;

    @Value("${APP_OFFICER_USERNAME:user}")
    private String officerUsername;

    @Value("${APP_OFFICER_PASSWORD:}")
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
        ensureAccount(adminUsername, adminPassword, User.Role.ADMIN, "admin");
    }

    private void createOfficerIfMissing() {
        ensureAccount(officerUsername, officerPassword, User.Role.OFFICER, "officer");
    }

    private void ensureAccount(String username, String password, User.Role role, String accountType) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("Skipping {} account seeding because credentials are not configured", accountType);
            return;
        }
        User account = userRepository.findByUsername(username)
                .orElseGet(() -> new User(username, passwordEncoder.encode(password), role));

        boolean changed = false;
        if (account.getRole() != role) {
            account.setRole(role);
            changed = true;
        }
        if (!account.isEnabled()) {
            account.setEnabled(true);
            changed = true;
        }
        if (account.getPasswordHash() == null
                || !passwordEncoder.matches(password, account.getPasswordHash())) {
            account.setPasswordHash(passwordEncoder.encode(password));
            changed = true;
        }

        if (account.getId() == null || changed) {
            userRepository.save(account);
            log.info("Configured {} account: {}", accountType, username);
        }
    }
}
