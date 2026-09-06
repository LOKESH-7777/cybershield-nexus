package com.cybershield;

import com.cybershield.dto.LoginRequest;
import com.cybershield.model.User;
import com.cybershield.repository.UserRepository;
import com.cybershield.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthAndSecurityTests — Validates authentication security, account lockout,
 * JWT verification, and Role-Based Access Control (RBAC).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAndSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("1. Successful Login returns valid JWT and role claims")
    void testSuccessfulLogin() throws Exception {
        LoginRequest req = new LoginRequest("admin", "Admin@123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("2. Failed Login with invalid password returns 401")
    void testInvalidPasswordFails() throws Exception {
        LoginRequest req = new LoginRequest("admin", "WrongPassword!999");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid username or password"));
    }

    @Test
    @DisplayName("3. Repeated failed logins trigger account lockout")
    void testAccountLockoutAfterFailures() throws Exception {
        String testUser = "viewer";
        User user = userRepository.findByUsername(testUser).orElseThrow();
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        LoginRequest badReq = new LoginRequest(testUser, "BadPassword!");

        // Send 5 failed login attempts
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(badReq)));
        }

        User lockedUser = userRepository.findByUsername(testUser).orElseThrow();
        assertTrue(lockedUser.getFailedLoginAttempts() >= 5, "Failed login counter should reach or exceed 5");
        assertNotNull(lockedUser.getLockedUntil(), "User lockedUntil must be set");
        assertTrue(lockedUser.getLockedUntil().isAfter(LocalDateTime.now()), "User should be locked in future");

        // Clean up: unlock viewer for other tests
        lockedUser.setFailedLoginAttempts(0);
        lockedUser.setLockedUntil(null);
        userRepository.save(lockedUser);
    }

    @Test
    @DisplayName("4. Unauthenticated requests to protected endpoints are blocked (401/403)")
    void testUnauthenticatedAccessBlocked() throws Exception {
        mockMvc.perform(get("/api/servers"))
                .andExpect(status().isForbidden()); // Spring Security 403 or 401 when unauthenticated
    }

    @Test
    @DisplayName("5. RBAC enforcement: VIEWER cannot invoke ADMIN-only mutating actions")
    void testRbacViewerRestrictedFromAdminActions() throws Exception {
        String viewerToken = jwtUtil.generateToken("viewer", "VIEWER");

        // Allowed for VIEWER
        mockMvc.perform(get("/api/incidents/count")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());

        // Forbidden for VIEWER (requires ADMIN)
        mockMvc.perform(put("/api/incidents/1/resolve")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"Test resolution\"}"))
                .andExpect(status().isForbidden());
    }
}
