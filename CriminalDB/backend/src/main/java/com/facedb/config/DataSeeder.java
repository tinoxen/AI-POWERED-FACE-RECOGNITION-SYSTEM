package com.facedb.config;

import com.facedb.model.User;
import com.facedb.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates a default admin account on first run so the app is usable
 * immediately. CHANGE THIS PASSWORD (or delete the user and create a new
 * one) before using this anywhere beyond your own local demo.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            User admin = new User("admin", passwordEncoder.encode("5623"), User.Role.ADMIN);
            userRepository.save(admin);
            System.out.println("Seeded default admin user -> username: admin / password: 5623");
            System.out.println("CHANGE THIS PASSWORD IMMEDIATELY.");
            return;
        }

        // Migrate the original demo account without changing its BCrypt hash.
        // Do not overwrite an account named "admin" if one was created later.
        userRepository.findByUsername("spectre")
                .filter(legacyAdmin -> userRepository.findByUsername("admin").isEmpty())
                .ifPresent(legacyAdmin -> {
                    legacyAdmin.setUsername("admin");
                    userRepository.save(legacyAdmin);
                    System.out.println("Renamed legacy default admin user from spectre to admin.");
                });
    }
}
