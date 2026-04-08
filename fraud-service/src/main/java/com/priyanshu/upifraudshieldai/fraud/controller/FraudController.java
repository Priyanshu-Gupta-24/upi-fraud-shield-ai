package com.priyanshu.upifraudshieldai.fraud.controller;

import com.priyanshu.upifraudshieldai.fraud.dto.FraudCheckRequest;
import com.priyanshu.upifraudshieldai.fraud.dto.FraudCheckResponse;
import com.priyanshu.upifraudshieldai.fraud.entity.FraudAlert;
import com.priyanshu.upifraudshieldai.fraud.service.FraudAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/fraud")
@RequiredArgsConstructor
@Slf4j
public class FraudController
{

    private final FraudAnalysisService fraudAnalysisService;

    /**
     * POST /api/fraud/check
     * Main endpoint — called by transaction-service via Feign.
     * Runs rules engine + RAG + LLM and returns fraud verdict.
     */
    @PostMapping("/check")
    public ResponseEntity<FraudCheckResponse> checkFraud(
            @RequestBody FraudCheckRequest request)
    {
        log.info("Fraud check request for txn: {}", request.getTransactionRef());
        return ResponseEntity.ok(fraudAnalysisService.analyzeTransaction(request));
    }

    /**
     * GET /api/fraud/alerts
     * Get all fraud alerts — for dashboard.
     */
    @GetMapping("/alerts")
    public ResponseEntity<List<FraudAlert>> getAllAlerts()
    {
        return ResponseEntity.ok(fraudAnalysisService.getAllAlerts());
    }

    /**
     * GET /api/fraud/alerts/open
     * Get only OPEN (unreviewed) alerts.
     */
    @GetMapping("/alerts/open")
    public ResponseEntity<List<FraudAlert>> getOpenAlerts()
    {
        return ResponseEntity.ok(fraudAnalysisService.getOpenAlerts());
    }

    /**
     * PATCH /api/fraud/alerts/{id}/status
     * Update alert status (REVIEWED, CONFIRMED, DISMISSED).
     * Body: { "status": "CONFIRMED" }
     */
    @PatchMapping("/alerts/{id}/status")
    public ResponseEntity<FraudAlert> updateStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body)
    {
        FraudAlert.AlertStatus status = FraudAlert.AlertStatus
                .valueOf(body.get("status").toUpperCase());
        return ResponseEntity.ok(fraudAnalysisService.updateAlertStatus(id, status));
    }

    /**
     * GET /api/fraud/health
     * Public endpoint for health check.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health()
    {
        return ResponseEntity.ok(Map.of(
                "status",  "UP",
                "service", "fraud-service",
                "port",    "8083"
        ));
    }
}