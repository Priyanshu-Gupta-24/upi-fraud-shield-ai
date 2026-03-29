package com.priyanshu.upifraudshieldai.user.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse
{

    private String token;

    @Builder.Default
    private String tokenType = "Bearer";

    private long expiresIn;   // in milliseconds

    private UserResponse user;
}