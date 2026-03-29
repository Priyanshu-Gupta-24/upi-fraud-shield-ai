package com.priyanshu.upifraudshieldai.user.service;

import com.priyanshu.upifraudshieldai.user.dto.*;
import com.priyanshu.upifraudshieldai.user.entity.UpiVpa;
import com.priyanshu.upifraudshieldai.user.entity.User;
import com.priyanshu.upifraudshieldai.user.exception.BadRequestException;
import com.priyanshu.upifraudshieldai.user.exception.DuplicateResourceException;
import com.priyanshu.upifraudshieldai.user.exception.ResourceNotFoundException;
import com.priyanshu.upifraudshieldai.user.kafka.UserEventPublisher;
import com.priyanshu.upifraudshieldai.user.repository.UpiVpaRepository;
import com.priyanshu.upifraudshieldai.user.repository.UserRepository;
import com.priyanshu.upifraudshieldai.user.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService
{

    private final UserRepository        userRepository;
    private final UpiVpaRepository      upiVpaRepository;
    private final PasswordEncoder       passwordEncoder;
    private final JwtUtil               jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserEventPublisher    eventPublisher;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 30;

    // AUTH

    public AuthResponse register(RegisterRequest req)
    {
        // Uniqueness checks
        if (userRepository.existsByUsername(req.getUsername()))
            throw new DuplicateResourceException("Username already taken: " + req.getUsername());
        if (userRepository.existsByEmail(req.getEmail()))
            throw new DuplicateResourceException("Email already registered: " + req.getEmail());
        if (req.getPhoneNumber() != null && !req.getPhoneNumber().isBlank()
                && userRepository.existsByPhoneNumber(req.getPhoneNumber()))
            throw new DuplicateResourceException("Phone number already registered");

        User user = User.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .fullName(req.getFullName())
                .phoneNumber(req.getPhoneNumber())
                .deviceId(req.getDeviceId())
                .role(User.Role.USER)
                .status(User.UserStatus.ACTIVE)
                .build();

        user = userRepository.save(user);
        log.info("New user registered: id={} username={}", user.getId(), user.getUsername());

        eventPublisher.publishUserRegistered(user);

        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest req)
    {
        // Load user first to apply lock logic
        User user = userRepository
                .findByUsernameOrEmail(req.getUsernameOrEmail(), req.getUsernameOrEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (user.isAccountLocked())
        {
            throw new BadRequestException(
                    "Account locked until " + user.getLockedUntil() +
                            ". Too many failed attempts.");
        }

        try
        {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            user.getUsername(), req.getPassword()));
        }
        catch (AuthenticationException e)
        {
            handleFailedLogin(user);
            throw new BadCredentialsException("Invalid credentials");
        }

        // Successful login — reset failed attempts, update metadata
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLogin(LocalDateTime.now());
        if (req.getDeviceId() != null && !req.getDeviceId().isBlank())
            user.setDeviceId(req.getDeviceId());
        userRepository.save(user);

        log.info("User logged in: {}", user.getUsername());
        eventPublisher.publishUserLoggedIn(user);

        return buildAuthResponse(user);
    }

    private void handleFailedLogin(User user)
    {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= MAX_FAILED_ATTEMPTS)
        {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
            user.setStatus(User.UserStatus.LOCKED);
            log.warn("User locked after {} failed attempts: {}", attempts, user.getUsername());
            eventPublisher.publishUserLocked(user);
        }

        userRepository.save(user);
    }

    private AuthResponse buildAuthResponse(User user)
    {
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole().name());
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getExpiration())
                .user(UserResponse.from(user))
                .build();
    }


    // USER PROFILE

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id)
    {
        return UserResponse.from(findUserById(id));
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByUsername(String username)
    {
        return UserResponse.from(
                userRepository.findByUsername(username)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "User", "username", username)));
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email)
    {
        return UserResponse.from(
                userRepository.findByEmail(email)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "User", "email", email)));
    }

    public UserResponse updateUser(UUID id, UpdateUserRequest req)
    {
        User user = findUserById(id);

        if (req.getFullName() != null && !req.getFullName().isBlank())
            user.setFullName(req.getFullName());

        if (req.getPhoneNumber() != null && !req.getPhoneNumber().isBlank())
        {
            if (userRepository.existsByPhoneNumber(req.getPhoneNumber())
                    && !req.getPhoneNumber().equals(user.getPhoneNumber()))
                throw new DuplicateResourceException("Phone number already in use");
            user.setPhoneNumber(req.getPhoneNumber());
        }

        if (req.getDeviceId() != null && !req.getDeviceId().isBlank())
            user.setDeviceId(req.getDeviceId());

        user = userRepository.save(user);
        log.info("User updated: {}", user.getUsername());
        eventPublisher.publishUserUpdated(user);
        return UserResponse.from(user);
    }

    public void changePassword(UUID id, ChangePasswordRequest req)
    {
        User user = findUserById(id);

        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPassword()))
            throw new BadRequestException("Current password is incorrect");

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        log.info("Password changed for user: {}", user.getUsername());
        eventPublisher.publishPasswordChanged(user);
    }

    // VPA MANAGEMENT

    public VpaResponse addVpa(UUID userId, VpaRequest req)
    {
        User user = findUserById(userId);

        if (upiVpaRepository.existsByVpa(req.getVpa()))
            throw new DuplicateResourceException("VPA already registered: " + req.getVpa());

        // If this VPA is set as primary, unset existing primary
        if (req.isPrimary())
        {
            upiVpaRepository.findByUserIdAndIsPrimaryTrue(userId)
                    .ifPresent(existing -> {
                        existing.setIsPrimary(false);
                        upiVpaRepository.save(existing);
                    });
        }

        UpiVpa vpa = UpiVpa.builder()
                .vpa(req.getVpa())
                .bankName(req.getBankName())
                .accountLast4(req.getAccountLast4())
                .isPrimary(req.isPrimary())
                .status(UpiVpa.VpaStatus.ACTIVE)
                .user(user)
                .build();

        vpa = upiVpaRepository.save(vpa);
        log.info("VPA added for user {}: {}", userId, vpa.getVpa());
        eventPublisher.publishVpaAdded(user, vpa);
        return VpaResponse.from(vpa);
    }

    @Transactional(readOnly = true)
    public List<VpaResponse> getVpasByUser(UUID userId)
    {
        findUserById(userId); // validate user exists
        return upiVpaRepository.findByUserId(userId)
                .stream()
                .map(VpaResponse::from)
                .toList();
    }

    public VpaResponse setVpaPrimary(UUID userId, UUID vpaId)
    {
        UpiVpa vpa = upiVpaRepository.findById(vpaId)
                .orElseThrow(() -> new ResourceNotFoundException("VPA", "id", vpaId));

        if (!vpa.getUser().getId().equals(userId))
            throw new BadRequestException("VPA does not belong to this user");

        // Unset current primary
        upiVpaRepository.findByUserIdAndIsPrimaryTrue(userId)
                .ifPresent(existing -> {
                    existing.setIsPrimary(false);
                    upiVpaRepository.save(existing);
                });

        vpa.setIsPrimary(true);
        return VpaResponse.from(upiVpaRepository.save(vpa));
    }

    public VpaResponse blockVpa(UUID userId, UUID vpaId) {
        UpiVpa vpa = upiVpaRepository.findById(vpaId)
                .orElseThrow(() -> new ResourceNotFoundException("VPA", "id", vpaId));

        if (!vpa.getUser().getId().equals(userId))
            throw new BadRequestException("VPA does not belong to this user");

        vpa.setStatus(UpiVpa.VpaStatus.BLOCKED);
        vpa = upiVpaRepository.save(vpa);
        log.warn("VPA blocked: {} for user {}", vpa.getVpa(), userId);
        return VpaResponse.from(vpa);
    }


    // ADMIN

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers()
    {
        return userRepository.findAll()
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    public UserResponse changeUserStatus(UUID id, User.UserStatus newStatus)
    {
        User user = findUserById(id);
        user.setStatus(newStatus);
        if (newStatus == User.UserStatus.ACTIVE)
        {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }
        user = userRepository.save(user);
        log.info("Admin changed status of user {} to {}", user.getUsername(), newStatus);
        eventPublisher.publishStatusChanged(user);
        return UserResponse.from(user);
    }

    // HELPERS

    private User findUserById(UUID id)
    {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }
}