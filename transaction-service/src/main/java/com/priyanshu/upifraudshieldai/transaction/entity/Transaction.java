package com.priyanshu.upifraudshieldai.transaction.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "transactions",
        indexes = {
                @Index(name = "idx_txn_sender_vpa",  columnList = "sender_vpa"),
                @Index(name = "idx_txn_receiver_vpa", columnList = "receiver_vpa"),
                @Index(name = "idx_txn_user_id",      columnList = "user_id"),
                @Index(name = "idx_txn_created_at",   columnList = "created_at"),
                @Index(name = "idx_txn_status",       columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction
{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "transaction_ref", unique = true, nullable = false, length = 50)
    private String transactionRef;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "sender_vpa", nullable = false, length = 100)
    private String senderVpa;

    @Column(name = "receiver_vpa", nullable = false, length = 100)
    private String receiverVpa;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 10)
    @Builder.Default
    private TransactionType transactionType = TransactionType.P2P;

    //  Fraud fields
    @Column(name = "is_fraud")
    private Boolean isFraud;

    @Column(name = "fraud_score")
    private Double fraudScore;

    @Column(name = "rules_triggered", length = 500)
    private String rulesTriggered;

    @Column(name = "fraud_explanation", columnDefinition = "TEXT")
    private String fraudExplanation;

    // Device metadata
    @Column(name = "device_id",  length = 100)
    private String deviceId;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "location",   length = 100)
    private String location;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    // Set explicitly in TransactionService — never null
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // Enums
    public enum TransactionStatus
    {
        PENDING,      // Just created, awaiting fraud check
        PROCESSING,   // Fraud check in progress
        COMPLETED,    // Passed fraud check, payment done
        FAILED,       // Payment failed for technical reasons
        FLAGGED,      // Fraud suspected but not blocked
        BLOCKED       // Fraud confirmed, payment stopped
    }

    public enum TransactionType
    {
        P2P,      // Person to person (e.g. send money to friend)
        P2M,      // Person to merchant (e.g. pay at shop)
        P2B,      // Person to bank (e.g. EMI payment)
        COLLECT   // Collect request (receiver pulls money)
    }
}