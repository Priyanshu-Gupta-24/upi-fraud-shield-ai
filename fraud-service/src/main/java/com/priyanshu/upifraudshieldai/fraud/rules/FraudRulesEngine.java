package com.priyanshu.upifraudshieldai.fraud.rules;

import com.priyanshu.upifraudshieldai.fraud.dto.FraudCheckRequest;
import com.priyanshu.upifraudshieldai.fraud.dto.RuleResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The fraud rules engine.

 * Each rule is independent and evaluates one specific fraud signal.
 * Rules are deterministic — same input always produces same output.
 * Each rule contributes a score (0.0–1.0) to the overall fraud score.
 * The final score is a weighted sum capped at 1.0.

 * Why rules BEFORE AI?
 * Rules fire in <5ms. The LLM takes 2-5 seconds.
 * If no rules fire, we skip the LLM entirely — fast path.
 * If rules fire, we use them as context for the LLM explanation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FraudRulesEngine
{

    private final JdbcTemplate jdbcTemplate;

    @Value("${fraud.rules.high-amount-threshold:50000}")
    private double highAmountThreshold;

    @Value("${fraud.rules.velocity-5min-limit:3}")
    private int velocity5MinLimit;

    @Value("${fraud.rules.velocity-1hr-limit:10}")
    private int velocity1HrLimit;

    @Value("${fraud.rules.daily-amount-limit:200000}")
    private double dailyAmountLimit;

    @Value("${fraud.rules.odd-hours-start:1}")
    private int oddHoursStart;

    @Value("${fraud.rules.odd-hours-end:4}")
    private int oddHoursEnd;

    @Value("${fraud.rules.new-vpa-daily-limit:3}")
    private int newVpaDailyLimit;

    @Value("${fraud.rules.round-amount-threshold:10000}")
    private double roundAmountThreshold;

    @Value("${fraud.rules.failed-attempts-limit:3}")
    private int failedAttemptsLimit;

    /**
     * Evaluates all rules against the transaction.
     * Returns list of RuleResult — one per rule that fired.
     */
    public List<RuleResult> evaluate(FraudCheckRequest request)
    {
        List<RuleResult> results = new ArrayList<>();

        results.add(checkHighAmount(request));
        results.add(checkVelocity5Min(request));
        results.add(checkVelocity1Hr(request));
        results.add(checkDailyAmountLimit(request));
        results.add(checkOddHours(request));
        results.add(checkNewDevice(request));
        results.add(checkRoundAmount(request));
        results.add(checkNewReceiverVpa(request));
        results.add(checkMultipleNewVpas(request));
        results.add(checkFirstTimeHighAmount(request));

        long triggered = results.stream().filter(RuleResult::isTriggered).count();
        log.debug("Rules evaluation for {}: {}/{} rules triggered",
                request.getTransactionRef(), triggered, results.size());

        return results;
    }

    // Rule 1: High Amount
    // Transactions above Rs 50,000 are flagged per RBI guidelines
    private RuleResult checkHighAmount(FraudCheckRequest req)
    {
        double amount = req.getAmount().doubleValue();
        boolean triggered = amount > highAmountThreshold;

        return RuleResult.builder()
                .ruleCode("RULE_HIGH_AMOUNT")
                .triggered(triggered)
                .scoreContribution(triggered ? 0.35 : 0.0)
                .reason(triggered
                        ? String.format("Amount Rs%.0f exceeds threshold Rs%.0f",
                        amount, highAmountThreshold)
                        : "Amount within normal range")
                .build();
    }

    // Rule 2: Velocity Check (5 minutes)
    // More than 3 transactions in 5 minutes = velocity attack
    private RuleResult checkVelocity5Min(FraudCheckRequest req)
    {
        try
        {
            LocalDateTime since = LocalDateTime.now().minusMinutes(5);
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM transactions " +
                            "WHERE sender_vpa = ? AND created_at >= ? " +
                            "AND status NOT IN ('FAILED', 'BLOCKED')",
                    Integer.class,
                    req.getSenderVpa(), since);

            int txnCount = count != null ? count : 0;
            boolean triggered = txnCount >= velocity5MinLimit;

            return RuleResult.builder()
                    .ruleCode("RULE_VELOCITY_5MIN")
                    .triggered(triggered)
                    .scoreContribution(triggered ? 0.40 : 0.0)
                    .reason(triggered
                            ? String.format("%d transactions in last 5 minutes (limit: %d)",
                            txnCount, velocity5MinLimit)
                            : "Normal transaction velocity")
                    .build();
        }
        catch (Exception e)
        {
            log.warn("Velocity 5min check failed: {}", e.getMessage());
            return RuleResult.builder().ruleCode("RULE_VELOCITY_5MIN")
                    .triggered(false).scoreContribution(0.0).reason("Check skipped").build();
        }
    }

    // Rule 3: Velocity Check (1 hour)
    // More than 10 transactions in 1 hour
    private RuleResult checkVelocity1Hr(FraudCheckRequest req)
    {
        try
        {
            LocalDateTime since = LocalDateTime.now().minusHours(1);
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM transactions " +
                            "WHERE sender_vpa = ? AND created_at >= ? " +
                            "AND status NOT IN ('FAILED', 'BLOCKED')",
                    Integer.class,
                    req.getSenderVpa(), since);

            int txnCount = count != null ? count : 0;
            boolean triggered = txnCount >= velocity1HrLimit;

            return RuleResult.builder()
                    .ruleCode("RULE_VELOCITY_1HR")
                    .triggered(triggered)
                    .scoreContribution(triggered ? 0.30 : 0.0)
                    .reason(triggered
                            ? String.format("%d transactions in last hour (limit: %d)",
                            txnCount, velocity1HrLimit)
                            : "Normal hourly velocity")
                    .build();
        }
        catch (Exception e)
        {
            log.warn("Velocity 1hr check failed: {}", e.getMessage());
            return RuleResult.builder().ruleCode("RULE_VELOCITY_1HR")
                    .triggered(false).scoreContribution(0.0).reason("Check skipped").build();
        }
    }

    // Rule 4: Daily Amount Limit
    // Total sent today exceeds Rs 2,00,000 (RBI UPI daily limit)
    private RuleResult checkDailyAmountLimit(FraudCheckRequest req) {
        try
        {
            LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
            Double totalToday = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(amount), 0) FROM transactions " +
                            "WHERE sender_vpa = ? AND created_at >= ? " +
                            "AND status NOT IN ('FAILED', 'BLOCKED')",
                    Double.class,
                    req.getSenderVpa(), startOfDay);

            double total = totalToday != null ? totalToday : 0.0;
            double newTotal = total + req.getAmount().doubleValue();
            boolean triggered = newTotal > dailyAmountLimit;

            return RuleResult.builder()
                    .ruleCode("RULE_DAILY_LIMIT")
                    .triggered(triggered)
                    .scoreContribution(triggered ? 0.45 : 0.0)
                    .reason(triggered
                            ? String.format("Daily total Rs%.0f would exceed limit Rs%.0f",
                            newTotal, dailyAmountLimit)
                            : "Within daily limit")
                    .build();
        }
        catch (Exception e)
        {
            log.warn("Daily limit check failed: {}", e.getMessage());
            return RuleResult.builder().ruleCode("RULE_DAILY_LIMIT")
                    .triggered(false).scoreContribution(0.0).reason("Check skipped").build();
        }
    }

    // Rule 5: Odd Hours
    // Transactions between 1 AM and 4 AM are suspicious
    private RuleResult checkOddHours(FraudCheckRequest req)
    {
        int hour = LocalDateTime.now().getHour();
        boolean triggered = hour >= oddHoursStart && hour < oddHoursEnd;

        return RuleResult.builder()
                .ruleCode("RULE_ODD_HOURS")
                .triggered(triggered)
                .scoreContribution(triggered ? 0.20 : 0.0)
                .reason(triggered
                        ? String.format("Transaction at %d:00 (odd hours: %d AM - %d AM)",
                        hour, oddHoursStart, oddHoursEnd)
                        : "Normal business hours")
                .build();
    }

    // Rule 6: New / Unknown Device
    // DeviceId not seen before in this user's transaction history
    private RuleResult checkNewDevice(FraudCheckRequest req)
    {
        if (req.getDeviceId() == null || req.getDeviceId().isBlank())
        {
            return RuleResult.builder()
                    .ruleCode("RULE_NEW_DEVICE")
                    .triggered(true)
                    .scoreContribution(0.25)
                    .reason("No device ID provided — unknown device")
                    .build();
        }

        try
        {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM transactions " +
                            "WHERE sender_vpa = ? AND device_id = ? AND status = 'COMPLETED'",
                    Integer.class,
                    req.getSenderVpa(), req.getDeviceId());

            boolean isNewDevice = count == null || count == 0;

            return RuleResult.builder()
                    .ruleCode("RULE_NEW_DEVICE")
                    .triggered(isNewDevice)
                    .scoreContribution(isNewDevice ? 0.25 : 0.0)
                    .reason(isNewDevice
                            ? "Transaction from unrecognized device: " + req.getDeviceId()
                            : "Known device")
                    .build();
        }
        catch (Exception e)
        {
            log.warn("New device check failed: {}", e.getMessage());
            return RuleResult.builder().ruleCode("RULE_NEW_DEVICE")
                    .triggered(false).scoreContribution(0.0).reason("Check skipped").build();
        }
    }

    // Rule 7: Suspicious Round Amount
    // Exact round amounts like Rs 50,000 or Rs 1,00,000 above threshold
    private RuleResult checkRoundAmount(FraudCheckRequest req)
    {
        double amount = req.getAmount().doubleValue();
        boolean isRound = amount % 1000 == 0 && amount >= roundAmountThreshold;

        return RuleResult.builder()
                .ruleCode("RULE_ROUND_AMOUNT")
                .triggered(isRound)
                .scoreContribution(isRound ? 0.15 : 0.0)
                .reason(isRound
                        ? String.format("Suspicious round amount Rs%.0f", amount)
                        : "Non-round amount")
                .build();
    }

    // Rule 8: New Receiver VPA
    // First time paying this receiver VPA — higher risk
    private RuleResult checkNewReceiverVpa(FraudCheckRequest req)
    {
        try
        {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM transactions " +
                            "WHERE sender_vpa = ? AND receiver_vpa = ? AND status = 'COMPLETED'",
                    Integer.class,
                    req.getSenderVpa(), req.getReceiverVpa());

            boolean isNew = count == null || count == 0;

            return RuleResult.builder()
                    .ruleCode("RULE_NEW_RECEIVER")
                    .triggered(isNew)
                    .scoreContribution(isNew ? 0.15 : 0.0)
                    .reason(isNew
                            ? "First transaction to VPA: " + req.getReceiverVpa()
                            : "Known receiver VPA")
                    .build();
        }
        catch (Exception e)
        {
            log.warn("New receiver check failed: {}", e.getMessage());
            return RuleResult.builder().ruleCode("RULE_NEW_RECEIVER")
                    .triggered(false).scoreContribution(0.0).reason("Check skipped").build();
        }
    }

    // Rule 9: Multiple New VPAs in 24 Hours
    // Paying many different new VPAs in 24 hours = account takeover pattern
    private RuleResult checkMultipleNewVpas(FraudCheckRequest req)
    {
        try
        {
            LocalDateTime since = LocalDateTime.now().minusHours(24);
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT receiver_vpa) FROM transactions " +
                            "WHERE sender_vpa = ? AND created_at >= ?",
                    Integer.class,
                    req.getSenderVpa(), since);

            int distinctReceivers = count != null ? count : 0;
            boolean triggered = distinctReceivers >= newVpaDailyLimit;

            return RuleResult.builder()
                    .ruleCode("RULE_MULTIPLE_NEW_VPAS")
                    .triggered(triggered)
                    .scoreContribution(triggered ? 0.30 : 0.0)
                    .reason(triggered
                            ? String.format("Paid %d different VPAs in 24 hours (limit: %d)",
                            distinctReceivers, newVpaDailyLimit)
                            : "Normal receiver pattern")
                    .build();
        }
        catch (Exception e)
        {
            log.warn("Multiple VPA check failed: {}", e.getMessage());
            return RuleResult.builder().ruleCode("RULE_MULTIPLE_NEW_VPAS")
                    .triggered(false).scoreContribution(0.0).reason("Check skipped").build();
        }
    }

    // Rule 10: First High-Value Transaction
    // Large amount to a new receiver with no transaction history
    private RuleResult checkFirstTimeHighAmount(FraudCheckRequest req)
    {
        try
        {
            boolean isHighAmount = req.getAmount().compareTo(
                    BigDecimal.valueOf(highAmountThreshold)) > 0;

            if (!isHighAmount)
            {
                return RuleResult.builder()
                        .ruleCode("RULE_FIRST_HIGH_VALUE")
                        .triggered(false).scoreContribution(0.0)
                        .reason("Amount below high-value threshold").build();
            }

            Integer totalHistory = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM transactions WHERE sender_vpa = ?",
                    Integer.class, req.getSenderVpa());

            boolean isNewSender = totalHistory == null || totalHistory <= 1;
            boolean triggered = isHighAmount && isNewSender;

            return RuleResult.builder()
                    .ruleCode("RULE_FIRST_HIGH_VALUE")
                    .triggered(triggered)
                    .scoreContribution(triggered ? 0.40 : 0.0)
                    .reason(triggered
                            ? String.format("High-value Rs%.0f as first/second transaction",
                            req.getAmount().doubleValue())
                            : "Established sender")
                    .build();
        }
        catch (Exception e)
        {
            log.warn("First high value check failed: {}", e.getMessage());
            return RuleResult.builder().ruleCode("RULE_FIRST_HIGH_VALUE")
                    .triggered(false).scoreContribution(0.0).reason("Check skipped").build();
        }
    }
}