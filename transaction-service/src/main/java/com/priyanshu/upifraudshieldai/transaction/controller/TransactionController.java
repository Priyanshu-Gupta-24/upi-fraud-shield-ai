package com.priyanshu.upifraudshieldai.transaction.controller;

import com.priyanshu.upifraudshieldai.transaction.dto.*;
import com.priyanshu.upifraudshieldai.transaction.entity.Transaction;
import com.priyanshu.upifraudshieldai.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController
{

    private final TransactionService transactionService;

    /**
     * POST /api/transactions
     * Submit a new UPI transaction for processing + fraud check.
     * This is the main endpoint.
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            @Valid @RequestBody CreateTransactionRequest request)
    {
        log.info("Create transaction: {} → {} ₹{}",
                request.getSenderVpa(), request.getReceiverVpa(), request.getAmount());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(transactionService.createTransaction(request));
    }

    /**
     * GET /api/transactions/{id}
     * Get full transaction details including fraud explanation.
     */
    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getById(@PathVariable UUID id)
    {
        return ResponseEntity.ok(transactionService.getById(id));
    }

    /**
     * GET /api/transactions/ref/{ref}
     * Get transaction by UPI reference number.
     */
    @GetMapping("/ref/{ref}")
    public ResponseEntity<TransactionResponse> getByRef(@PathVariable String ref)
    {
        return ResponseEntity.ok(transactionService.getByRef(ref));
    }

    /**
     * GET /api/transactions/user/{userId}
     * Get all transactions for a user (for history page).
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<TransactionSummaryResponse>> getByUser(
            @PathVariable UUID userId)
    {
        return ResponseEntity.ok(transactionService.getByUserId(userId));
    }

    /**
     * GET /api/transactions/user/{userId}/recent?limit=10
     * Get recent N transactions (for dashboard widget).
     */
    @GetMapping("/user/{userId}/recent")
    public ResponseEntity<List<TransactionSummaryResponse>> getRecent(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "10") int limit)
    {
        return ResponseEntity.ok(transactionService.getRecentByUserId(userId, limit));
    }

    /**
     * GET /api/transactions/fraudulent
     * Get all fraud-flagged transactions — ADMIN only.
     */
    @GetMapping("/fraudulent")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ANALYST')")
    public ResponseEntity<List<TransactionSummaryResponse>> getFraudulent()
    {
        return ResponseEntity.ok(transactionService.getAllFraudulent());
    }

    /**
     * GET /api/transactions/status/{status}
     * Get transactions by status (PENDING, FLAGGED, BLOCKED etc.) — ADMIN only.
     */
    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ANALYST')")
    public ResponseEntity<List<TransactionSummaryResponse>> getByStatus(
            @PathVariable String status)
    {
        Transaction.TransactionStatus txnStatus =
                Transaction.TransactionStatus.valueOf(status.toUpperCase());
        return ResponseEntity.ok(transactionService.getByStatus(txnStatus));
    }

    /**
     * GET /api/transactions/health
     * Public endpoint for health check.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health()
    {
        return ResponseEntity.ok(Map.of(
                "status",  "UP",
                "service", "transaction-service",
                "port",    "8082"
        ));
    }
}