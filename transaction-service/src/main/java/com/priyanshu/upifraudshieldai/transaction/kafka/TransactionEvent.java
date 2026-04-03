package com.priyanshu.upifraudshieldai.transaction.kafka;

import com.priyanshu.upifraudshieldai.transaction.entity.Transaction;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionEvent
{

    public enum EventType {
        TRANSACTION_CREATED,
        TRANSACTION_COMPLETED,
        TRANSACTION_FLAGGED,
        TRANSACTION_BLOCKED,
        TRANSACTION_FAILED
    }

    private EventType eventType;
    private UUID transactionId;
    private String transactionRef;
    private UUID userId;
    private String senderVpa;
    private String receiverVpa;
    private BigDecimal amount;
    private Transaction.TransactionStatus status;
    private Boolean isFraud;
    private Double fraudScore;
    private String rulesTriggered;

    @Builder.Default
    private Instant timestamp = Instant.now();
}