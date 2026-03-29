package com.priyanshu.upifraudshieldai.user.dto;

import com.priyanshu.upifraudshieldai.user.entity.User;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse
{

    private UUID id;
    private String username;
    private String email;
    private String fullName;
    private String phoneNumber;
    private User.Role role;
    private User.UserStatus status;
    private LocalDateTime lastLogin;
    private LocalDateTime createdAt;

    public static UserResponse from(User user)
    {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .status(user.getStatus())
                .lastLogin(user.getLastLogin())
                .createdAt(user.getCreatedAt())
                .build();
    }
}