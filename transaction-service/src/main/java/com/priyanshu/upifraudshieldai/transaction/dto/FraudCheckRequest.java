package com.priyanshu.upifraudshieldai.transaction.dto;

import com.priyanshu.upifraudshieldai.transaction.entity.Transaction;
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
    private Transaction.TransactionType transactionType;
    private String deviceId;
    private String ipAddress;
    private String location;
    private LocalDateTime createdAt;

    public static FraudCheckRequest from(Transaction t)
    {
        return FraudCheckRequest.builder()
                .transactionId(t.getId())
                .transactionRef(t.getTransactionRef())
                .userId(t.getUserId())
                .senderVpa(t.getSenderVpa())
                .receiverVpa(t.getReceiverVpa())
                .amount(t.getAmount())
                .transactionType(t.getTransactionType())
                .deviceId(t.getDeviceId())
                .ipAddress(t.getIpAddress())
                .location(t.getLocation())
                .createdAt(t.getCreatedAt())
                .build();
    }
}