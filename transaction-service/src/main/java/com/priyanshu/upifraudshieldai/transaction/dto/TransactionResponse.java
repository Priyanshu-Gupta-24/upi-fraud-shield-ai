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
public class TransactionResponse
{

    private UUID id;
    private String transactionRef;
    private UUID userId;
    private String senderVpa;
    private String receiverVpa;
    private BigDecimal amount;
    private String description;
    private Transaction.TransactionStatus status;
    private Transaction.TransactionType transactionType;

    // Fraud information
    private Boolean isFraud;
    private Double fraudScore;
    private String rulesTriggered;
    private String fraudExplanation;

    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    // Helper: build from entity
    public static TransactionResponse from(Transaction t)
    {
        return TransactionResponse.builder()
                .id(t.getId())
                .transactionRef(t.getTransactionRef())
                .userId(t.getUserId())
                .senderVpa(t.getSenderVpa())
                .receiverVpa(t.getReceiverVpa())
                .amount(t.getAmount())
                .description(t.getDescription())
                .status(t.getStatus())
                .transactionType(t.getTransactionType())
                .isFraud(t.getIsFraud())
                .fraudScore(t.getFraudScore())
                .rulesTriggered(t.getRulesTriggered())
                .fraudExplanation(t.getFraudExplanation())
                .createdAt(t.getCreatedAt())
                .completedAt(t.getCompletedAt())
                .build();
    }
}