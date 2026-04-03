package com.priyanshu.upifraudshieldai.transaction.dto;

import com.priyanshu.upifraudshieldai.transaction.entity.Transaction;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTransactionRequest
{

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotBlank(message = "Sender VPA is required")
    @Pattern(
            regexp = "^[a-zA-Z0-9._-]+@[a-zA-Z]+$",
            message = "Invalid sender VPA format"
    )
    private String senderVpa;

    @NotBlank(message = "Receiver VPA is required")
    @Pattern(
            regexp = "^[a-zA-Z0-9._-]+@[a-zA-Z]+$",
            message = "Invalid receiver VPA format"
    )
    private String receiverVpa;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Minimum transaction amount is ₹1")
    @DecimalMax(value = "200000.00", message = "Maximum transaction amount is ₹2,00,000")
    private BigDecimal amount;

    @Size(max = 255, message = "Description too long")
    private String description;

    @Builder.Default
    private Transaction.TransactionType transactionType = Transaction.TransactionType.P2P;

    private String deviceId;
    private String ipAddress;
    private String location;
    private String userAgent;
}