package com.priyanshu.upifraudshieldai.fraud.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "fraud_alerts", indexes =
        {
        @Index(name = "idx_alert_transaction_id", columnList = "transaction_id"),
        @Index(name = "idx_alert_sender_vpa",     columnList = "sender_vpa"),
        @Index(name = "idx_alert_created_at",     columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FraudAlert
{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "transaction_ref", nullable = false, length = 50)
    private String transactionRef;

    @Column(name = "sender_vpa", nullable = false, length = 100)
    private String senderVpa;

    @Column(name = "receiver_vpa", nullable = false, length = 100)
    private String receiverVpa;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "fraud_score", nullable = false)
    private Double fraudScore;

    @Column(name = "rules_triggered", length = 500)
    private String rulesTriggered;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public enum AlertStatus
    {
        OPEN,       // New alert, not yet reviewed
        REVIEWED,   // Analyst has reviewed it
        CONFIRMED,  // Confirmed as fraud
        DISMISSED   // False positive, dismissed
    }
}