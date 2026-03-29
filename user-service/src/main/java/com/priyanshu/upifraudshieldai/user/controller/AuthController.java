package com.priyanshu.upifraudshieldai.user.controller;

import com.priyanshu.upifraudshieldai.user.dto.AuthResponse;
import com.priyanshu.upifraudshieldai.user.dto.LoginRequest;
import com.priyanshu.upifraudshieldai.user.dto.RegisterRequest;
import com.priyanshu.upifraudshieldai.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController
{

    private final UserService userService;

    /**
     * POST /api/auth/register
     * Register a new user and return JWT token.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request)
    {
        log.info("Register request for username: {}", request.getUsername());
        AuthResponse response = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/auth/login
     * Authenticate and return JWT token.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request)
    {
        log.info("Login attempt for: {}", request.getUsernameOrEmail());
        return ResponseEntity.ok(userService.login(request));
    }

    /**
     * GET /api/auth/health
     * Simple health check (public).
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health()
    {
        return ResponseEntity.ok(Map.of(
                "status",  "UP",
                "service", "user-service",
                "port",    "8081"
        ));
    }
}