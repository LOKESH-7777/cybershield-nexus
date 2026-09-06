package com.cybershield.config;

import com.cybershield.model.User;
import com.cybershield.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * DataInitializer — seeds initial data on application startup.
 *
 * On first run:
 *   - Creates the default ADMIN user: admin / Admin@123
 *   - Creates a test SERVER_ADMIN user: serveradmin / Admin@123
 *   - Creates a test VIEWER user: viewer / Admin@123
 *
 * These are the accounts used in our attack demonstrations.
 * The 'viewer' account is used for Attack #2 (IDOR) and Attack #3 (JWT tampering).
 *
 * IMPORTANT: In production, disable this class and use a proper onboarding flow.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedUser("admin",       "admin@cybershield.local",
                 "Admin@123",   User.Role.ADMIN,       "IT Security");

        seedUser("serveradmin", "serveradmin@cybershield.local",
                 "Admin@123",   User.Role.SERVER_ADMIN, "Infrastructure");

        seedUser("viewer",      "viewer@cybershield.local",
                 "Admin@123",   User.Role.VIEWER,       "Monitoring");

        log.info("===========================================================");
        log.info("  CyberShield Nexus is ready!");
        log.info("  API running at: http://localhost:8081");
        log.info("  Default accounts:");
        log.info("    admin / Admin@123       → ADMIN role");
        log.info("    serveradmin / Admin@123 → SERVER_ADMIN role");
        log.info("    viewer / Admin@123      → VIEWER role");
        log.info("===========================================================");
    }

    private void seedUser(String username, String email,
                          String rawPassword, User.Role role, String department) {
        if (!userRepository.existsByUsername(username)) {
            User user = User.builder()
                    .username(username)
                    .email(email)
                    .passwordHash(passwordEncoder.encode(rawPassword))
                    .role(role)
                    .department(department)
                    .isActive(true)
                    .failedLoginAttempts(0)
                    .build();
            userRepository.save(user);
            log.info("Created user: '{}' with role '{}'", username, role);
        } else {
            log.debug("User '{}' already exists — skipping seed", username);
        }
    }
}
