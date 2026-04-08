package com.priyanshu.upifraudshieldai.fraud.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FraudCheckRequest
{

    private UUID transactionId;
    private String transactionRef;
    private UUID userId;
    private String senderVpa;
    private String receiverVpa;
    private BigDecimal amount;
    private String transactionType;
    private String deviceId;
    private String ipAddress;
    private String location;
    private LocalDateTime createdAt;
}