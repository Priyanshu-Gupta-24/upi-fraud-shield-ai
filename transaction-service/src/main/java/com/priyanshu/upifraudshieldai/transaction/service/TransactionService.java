package com.priyanshu.upifraudshieldai.transaction.service;

import com.priyanshu.upifraudshieldai.transaction.client.FraudServiceClient;
import com.priyanshu.upifraudshieldai.transaction.dto.*;
import com.priyanshu.upifraudshieldai.transaction.entity.Transaction;
import com.priyanshu.upifraudshieldai.transaction.exception.ResourceNotFoundException;
import com.priyanshu.upifraudshieldai.transaction.kafka.TransactionEventPublisher;
import com.priyanshu.upifraudshieldai.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TransactionService
{

    private final TransactionRepository     transactionRepository;
    private final FraudServiceClient        fraudServiceClient;
    private final TransactionEventPublisher eventPublisher;

    private static final Random RANDOM = new Random();

    // CREATE TRANSACTION

    public TransactionResponse createTransaction(CreateTransactionRequest req)
    {
        log.info("New transaction: {} -> {} Rs{}",
                req.getSenderVpa(), req.getReceiverVpa(), req.getAmount());

        // 1. Save as PENDING — createdAt set explicitly (never null)
        Transaction txn = Transaction.builder()
                .transactionRef(generateTransactionRef())
                .userId(req.getUserId())
                .senderVpa(req.getSenderVpa())
                .receiverVpa(req.getReceiverVpa())
                .amount(req.getAmount())
                .description(req.getDescription())
                .transactionType(req.getTransactionType())
                .status(Transaction.TransactionStatus.PENDING)
                .deviceId(req.getDeviceId())
                .ipAddress(req.getIpAddress())
                .location(req.getLocation())
                .userAgent(req.getUserAgent())
                .createdAt(LocalDateTime.now())
                .build();

        txn = transactionRepository.save(txn);
        log.debug("Saved PENDING: {}", txn.getTransactionRef());

        // 2. Publish created event to Kafka
        eventPublisher.publishCreated(txn);

        // 3. Mark PROCESSING
        txn.setStatus(Transaction.TransactionStatus.PROCESSING);
        txn = transactionRepository.save(txn);

        // 4. Call fraud-service — GUARANTEED to never throw
        FraudCheckResponse fraudResp = callFraudServiceSafely(txn);

        log.info("Fraud result for [{}]: fraud={} score={} recommendation={}",
                txn.getTransactionRef(),
                fraudResp.isFraud(),
                fraudResp.getFraudScore(),
                fraudResp.getRecommendation());

        // 5. Write fraud verdict onto the transaction
        txn.setIsFraud(fraudResp.isFraud());
        txn.setFraudScore(fraudResp.getFraudScore());
        txn.setFraudExplanation(fraudResp.getExplanation());

        if (fraudResp.getTriggeredRules() != null
                && !fraudResp.getTriggeredRules().isEmpty()) {
            txn.setRulesTriggered(String.join(",", fraudResp.getTriggeredRules()));
        }

        // 6. Final status based on recommendation
        LocalDateTime now = LocalDateTime.now();
        switch (fraudResp.getRecommendation()) {
            case "BLOCK" -> {
                txn.setStatus(Transaction.TransactionStatus.BLOCKED);
                txn.setCompletedAt(now);
                transactionRepository.save(txn);
                eventPublisher.publishBlocked(txn);
                log.warn("BLOCKED: {}", txn.getTransactionRef());
            }
            case "FLAG" -> {
                txn.setStatus(Transaction.TransactionStatus.FLAGGED);
                txn.setCompletedAt(now);
                transactionRepository.save(txn);
                eventPublisher.publishFlagged(txn);
                log.warn("FLAGGED: {}", txn.getTransactionRef());
            }
            default -> {
                txn.setStatus(Transaction.TransactionStatus.COMPLETED);
                txn.setCompletedAt(now);
                transactionRepository.save(txn);
                eventPublisher.publishCompleted(txn);
                log.info("COMPLETED: {}", txn.getTransactionRef());
            }
        }

        return TransactionResponse.from(txn);
    }

    /**
     * Calls fraud-service via Feign.
     * If ANYTHING goes wrong (service not in Eureka, timeout, connection refused, HTTP error) — returns a safe ALLOW default.
     * This method NEVER throws. The transaction is NEVER stuck as FAILED.
     */
    private FraudCheckResponse callFraudServiceSafely(Transaction txn)
    {
        try
        {
            return fraudServiceClient.checkFraud(FraudCheckRequest.from(txn));
        }
        catch (Exception e)
        {
            log.warn("Fraud service call failed for [{}] — defaulting to ALLOW. Error: {}",
                    txn.getTransactionRef(), e.getMessage());
            return FraudCheckResponse.builder()
                    .fraud(false)
                    .fraudScore(0.0)
                    .triggeredRules(List.of())
                    .recommendation("ALLOW")
                    .explanation("Fraud check service temporarily unavailable. " +
                            "Transaction allowed by default.")
                    .build();
        }
    }


    // READ OPERATIONS

    @Transactional(readOnly = true)
    public TransactionResponse getById(UUID id)
    {
        return TransactionResponse.from(findById(id));
    }

    @Transactional(readOnly = true)
    public TransactionResponse getByRef(String ref)
    {
        return TransactionResponse.from(
                transactionRepository.findByTransactionRef(ref)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Transaction not found with ref: " + ref)));
    }

    @Transactional(readOnly = true)
    public List<TransactionSummaryResponse> getByUserId(UUID userId)
    {
        return transactionRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(TransactionSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TransactionSummaryResponse> getRecentByUserId(UUID userId, int limit)
    {
        return transactionRepository
                .findRecentByUserId(userId, limit)
                .stream()
                .map(TransactionSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TransactionSummaryResponse> getAllFraudulent()
    {
        return transactionRepository
                .findByIsFraudTrueOrderByCreatedAtDesc()
                .stream()
                .map(TransactionSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TransactionSummaryResponse> getByStatus(
            Transaction.TransactionStatus status) {
        return transactionRepository
                .findByStatusOrderByCreatedAtDesc(status)
                .stream()
                .map(TransactionSummaryResponse::from)
                .toList();
    }

    // HELPERS

    private Transaction findById(UUID id)
    {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transaction not found: " + id));
    }

    private String generateTransactionRef()
    {
        String date   = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String random = String.format("%04d", RANDOM.nextInt(10000));
        return date + random;
    }
}