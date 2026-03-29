package com.priyanshu.upifraudshieldai.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "upi_vpas",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_upi_vpas_vpa", columnNames = "vpa")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "user")
public class UpiVpa
{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    // e.g.  priyanshu@okaxis, priyanshu@ybl
    @Column(nullable = false, unique = true, length = 100)
    private String vpa;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "account_last4", length = 4)
    private String accountLast4;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private VpaStatus status = VpaStatus.ACTIVE;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private Boolean isPrimary = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_upi_vpas_user"))
    private User user;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum VpaStatus
    {
        ACTIVE, INACTIVE, BLOCKED
    }
}