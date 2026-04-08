package com.priyanshu.upifraudshieldai.fraud.service;

import com.priyanshu.upifraudshieldai.fraud.dto.FraudCheckRequest;
import com.priyanshu.upifraudshieldai.fraud.dto.FraudCheckResponse;
import com.priyanshu.upifraudshieldai.fraud.dto.RuleResult;
import com.priyanshu.upifraudshieldai.fraud.entity.FraudAlert;
import com.priyanshu.upifraudshieldai.fraud.kafka.FraudAlertPublisher;
import com.priyanshu.upifraudshieldai.fraud.repository.FraudAlertRepository;
import com.priyanshu.upifraudshieldai.fraud.rules.FraudRulesEngine;
import com.priyanshu.upifraudshieldai.fraud.service.RagService.RagContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * FraudAnalysisService — orchestrates the complete pipeline.
 * Pipeline flow:
 * 1. Rules Engine   → deterministic, <5ms
 * 2. Score calc     → weighted sum, capped at 1.0
 * 3. Recommendation → ALLOW / FLAG / BLOCK
 * 4. RAG            → 3-category context retrieval (only if rules fired)
 * 5. LLM            → 3-section structured explanation
 * 6. Persist        → FraudAlert saved to DB (only if isFraud)
 * 7. Kafka          → fraud-alerts topic published
 * 8. Response       → FraudCheckResponse back to transaction-service
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class FraudAnalysisService
{

    private final FraudRulesEngine     rulesEngine;
    private final RagService           ragService;
    private final ExplanationService   explanationService;
    private final FraudAlertRepository alertRepository;
    private final FraudAlertPublisher  alertPublisher;

    private static final double BLOCK_THRESHOLD = 0.70;
    private static final double FLAG_THRESHOLD  = 0.40;

    public FraudCheckResponse analyzeTransaction(FraudCheckRequest request)
    {
        log.info("Analyzing txn: {} Rs{} {} → {}",
                request.getTransactionRef(),
                request.getAmount(),
                request.getSenderVpa(),
                request.getReceiverVpa());

        // 1. Run all 10 rules
        List<RuleResult> allResults = rulesEngine.evaluate(request);
        List<RuleResult> triggered  = allResults.stream()
                .filter(RuleResult::isTriggered).toList();

        // 2. Calculate fraud score (sum of contributions, capped at 1.0)
        double rawScore   = triggered.stream()
                .mapToDouble(RuleResult::getScoreContribution).sum();
        double fraudScore = Math.min(rawScore, 1.0);

        // 3. Determine recommendation
        String recommendation = determineRecommendation(fraudScore);
        boolean isFraud = fraudScore >= FLAG_THRESHOLD;

        log.info("Score={} Recommendation={} Rules triggered={}",
                String.format("%.2f", fraudScore), recommendation, triggered.size());

        // 4. Generate explanation
        // For clean transactions: skip LLM entirely (fast path)
        // For flagged/blocked: retrieve all 3 RAG categories + call LLM
        String explanation;
        if (triggered.isEmpty())
        {
            explanation = "Transaction passed all fraud detection checks. " +
                    "No suspicious patterns detected against RBI/NPCI guidelines.";
        }
        else
        {
            // RAG: retrieve why-flagged + liability + next-steps
            RagContext ragContext = ragService.retrieveContext(
                    request, triggered, recommendation);

            // LLM: generate structured 3-section explanation
            explanation = explanationService.generateExplanation(
                    request, triggered, fraudScore, recommendation, ragContext);
        }

        // 5. Persist fraud alert (only for flagged/blocked)
        if (isFraud)
            saveFraudAlert(request, triggered, fraudScore, explanation, recommendation);

        // 6. Build response
        List<String> triggeredCodes = triggered.stream()
                .map(RuleResult::getRuleCode).toList();

        FraudCheckResponse response = FraudCheckResponse.builder()
                .fraud(isFraud)
                .fraudScore(fraudScore)
                .triggeredRules(triggeredCodes)
                .explanation(explanation)
                .recommendation(recommendation)
                .build();

        log.info("Analysis complete for {}: {} (score={}, triggered={})",
                request.getTransactionRef(),
                recommendation,
                String.format("%.2f", fraudScore),
                triggeredCodes);

        return response;
    }

    private String determineRecommendation(double score)
    {
        if (score >= BLOCK_THRESHOLD) return "BLOCK";
        if (score >= FLAG_THRESHOLD)  return "FLAG";
        return "ALLOW";
    }

    private void saveFraudAlert(
            FraudCheckRequest req,
            List<RuleResult> triggered,
            double score,
            String explanation,
            String recommendation) {
        try
        {
            String rulesCsv = triggered.stream()
                    .map(RuleResult::getRuleCode)
                    .collect(java.util.stream.Collectors.joining(","));

            FraudAlert alert = FraudAlert.builder()
                    .transactionId(req.getTransactionId())
                    .transactionRef(req.getTransactionRef())
                    .senderVpa(req.getSenderVpa())
                    .receiverVpa(req.getReceiverVpa())
                    .amount(req.getAmount())
                    .fraudScore(score)
                    .rulesTriggered(rulesCsv)
                    .explanation(explanation)
                    .status(FraudAlert.AlertStatus.OPEN)
                    .createdAt(LocalDateTime.now())
                    .build();

            alertRepository.save(alert);
            alertPublisher.publishAlert(alert, recommendation);
            log.info("Fraud alert saved and published for txn: {}", req.getTransactionRef());

        }
        catch (Exception e)
        {
            log.error("Failed to save fraud alert for {}: {}",
                    req.getTransactionRef(), e.getMessage());
        }
    }

    // Dashboard queries

    @Transactional(readOnly = true)
    public List<FraudAlert> getAllAlerts()
    {
        return alertRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<FraudAlert> getOpenAlerts()
    {
        return alertRepository.findByStatusOrderByCreatedAtDesc(
                FraudAlert.AlertStatus.OPEN);
    }

    public FraudAlert updateAlertStatus(UUID alertId, FraudAlert.AlertStatus status)
    {
        FraudAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Alert not found: " + alertId));
        alert.setStatus(status);
        return alertRepository.save(alert);
    }
}