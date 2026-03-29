package com.priyanshu.upifraudshieldai.user.controller;

import com.priyanshu.upifraudshieldai.user.dto.*;
import com.priyanshu.upifraudshieldai.user.entity.User;
import com.priyanshu.upifraudshieldai.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    //Profile Endpoints

    /**
     * GET /api/users/me
     * Get the currently authenticated user's profile.
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails)
    {
        return ResponseEntity.ok(
                userService.getUserByUsername(userDetails.getUsername()));
    }

    /**
     * GET /api/users/{id}
     * Get user by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id)
    {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    /**
     * PUT /api/users/{id}
     * Update user profile (fullName, phone, deviceId).
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request)
    {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    /**
     * POST /api/users/{id}/change-password
     * Change password — requires current password.
     */
    @PostMapping("/{id}/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @PathVariable UUID id,
            @Valid @RequestBody ChangePasswordRequest request)
    {
        userService.changePassword(id, request);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    // VPA Endpoints

    /**
     * GET /api/users/{id}/vpas
     * List all VPAs for a user.
     */
    @GetMapping("/{id}/vpas")
    public ResponseEntity<List<VpaResponse>> getVpas(@PathVariable UUID id)
    {
        return ResponseEntity.ok(userService.getVpasByUser(id));
    }

    /**
     * POST /api/users/{id}/vpas
     * Add a new UPI VPA to a user account.
     */
    @PostMapping("/{id}/vpas")
    public ResponseEntity<VpaResponse> addVpa(
            @PathVariable UUID id,
            @Valid @RequestBody VpaRequest request)
    {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(userService.addVpa(id, request));
    }

    /**
     * PATCH /api/users/{userId}/vpas/{vpaId}/primary
     * Set a VPA as the primary VPA.
     */
    @PatchMapping("/{userId}/vpas/{vpaId}/primary")
    public ResponseEntity<VpaResponse> setVpaPrimary(
            @PathVariable UUID userId,
            @PathVariable UUID vpaId)
    {
        return ResponseEntity.ok(userService.setVpaPrimary(userId, vpaId));
    }

    /**
     * PATCH /api/users/{userId}/vpas/{vpaId}/block
     * Block a VPA (e.g. reported as compromised).
     */
    @PatchMapping("/{userId}/vpas/{vpaId}/block")
    public ResponseEntity<VpaResponse> blockVpa(
            @PathVariable UUID userId,
            @PathVariable UUID vpaId)
    {
        return ResponseEntity.ok(userService.blockVpa(userId, vpaId));
    }

    // ADMIN ENDPOINTS

    /**
     * GET /api/users
     * Get all users — ADMIN only.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    /**
     * PATCH /api/users/{id}/status
     * Change user status (ACTIVE / SUSPENDED / LOCKED) — ADMIN only.

     * Request body: { "status": "SUSPENDED" }
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> changeStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        User.UserStatus newStatus = User.UserStatus.valueOf(
                body.get("status").toUpperCase());
        return ResponseEntity.ok(userService.changeUserStatus(id, newStatus));
    }
}