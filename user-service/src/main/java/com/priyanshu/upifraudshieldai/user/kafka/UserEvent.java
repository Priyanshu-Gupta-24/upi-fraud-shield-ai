package com.priyanshu.upifraudshieldai.user.kafka;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEvent
{

    public enum EventType
    {
        USER_REGISTERED,
        USER_UPDATED,
        USER_STATUS_CHANGED,
        USER_LOGGED_IN,
        USER_LOCKED,
        VPA_ADDED,
        VPA_BLOCKED,
        PASSWORD_CHANGED
    }

    private EventType eventType;
    private UUID userId;
    private String username;
    private String email;
    private String phoneNumber;
    private String deviceId;
    private String metadata;

    @Builder.Default
    private Instant timestamp = Instant.now();
}