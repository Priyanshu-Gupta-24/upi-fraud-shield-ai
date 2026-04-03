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
public class TransactionSummaryResponse
{

    private UUID id;
    private String transactionRef;
    private String senderVpa;
    private String receiverVpa;
    private BigDecimal amount;
    private Transaction.TransactionStatus status;
    private Boolean isFraud;
    private Double fraudScore;
    private LocalDateTime createdAt;

    public static TransactionSummaryResponse from(Transaction t)
    {
        return TransactionSummaryResponse.builder()
                .id(t.getId())
                .transactionRef(t.getTransactionRef())
                .senderVpa(t.getSenderVpa())
                .receiverVpa(t.getReceiverVpa())
                .amount(t.getAmount())
                .status(t.getStatus())
                .isFraud(t.getIsFraud())
                .fraudScore(t.getFraudScore())
                .createdAt(t.getCreatedAt())
                .build();
    }
}