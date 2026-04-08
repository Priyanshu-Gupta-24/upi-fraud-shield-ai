package com.priyanshu.upifraudshieldai.fraud.service;

import com.priyanshu.upifraudshieldai.fraud.dto.FraudCheckRequest;
import com.priyanshu.upifraudshieldai.fraud.dto.RuleResult;
import com.priyanshu.upifraudshieldai.fraud.service.RagService.RagContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ExplanationService — AI explainability layer.

 * Produces a structured 3-section explanation using Gemini:
 *   WHY FLAGGED:    specific fraud signals grounded in RBI/NPCI rules
 *   YOUR RIGHTS:    customer liability and zero-liability window
 *   WHAT TO DO NOW: actionable steps with 1930, cybercrime.gov.in, cms.rbi.org.in

 * Parameters (all required — none optional):
 *   request        — the original transaction being analyzed
 *   triggeredRules — list of fraud rules that fired
 *   fraudScore     — computed score 0.0–1.0
 *   recommendation — ALLOW / FLAG / BLOCK (drives prompt tone + RAG queries)
 *   ragContext      — three-category guideline text from pgvector
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExplanationService
{

    private final ChatClient chatClient;

    /**
     * Main method — tries Gemini first, falls back to rule-based text.
     */
    public String generateExplanation(
            FraudCheckRequest request,
            List<RuleResult> triggeredRules,
            double fraudScore,
            String recommendation,      // ← required: ALLOW / FLAG / BLOCK
            RagContext ragContext) {

        try
        {
            String prompt = buildPrompt(
                    request, triggeredRules, fraudScore, recommendation, ragContext);

            log.debug("Calling Gemini for txn: {}", request.getTransactionRef());

            String explanation = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (explanation != null)
            {
                log.info("Gemini explanation generated for txn: {} ({} chars)",
                        request.getTransactionRef(), explanation.length());
            }

            assert explanation != null;
            return explanation.trim();

        }
        catch (Exception e)
        {
            log.warn("Gemini unavailable for [{}] — using rule-based fallback. Error: {}",
                    request.getTransactionRef(), e.getMessage());
            return buildFallbackExplanation(
                    request, triggeredRules, fraudScore, recommendation, ragContext);
        }
    }

    /**
     * Builds the structured 3-section prompt for Gemini.
     * Each section is grounded in a different RAG category.
     * recommendation drives the tone: BLOCK = urgent, FLAG = cautious, ALLOW = informational.
     */
    private String buildPrompt(
            FraudCheckRequest req,
            List<RuleResult> rules,
            double score,
            String recommendation,
            RagContext ctx) {

        String actionWord = switch (recommendation)
        {
            case "BLOCK" -> "BLOCKED (transaction declined)";
            case "FLAG"  -> "FLAGGED FOR REVIEW (transaction allowed but marked suspicious)";
            default      -> "ALLOWED (transaction passed fraud checks)";
        };

        String rulesText = rules.stream()
                .map(r -> "- " + r.getRuleCode() + ": " + r.getReason())
                .collect(Collectors.joining("\n"));

        // Only include guideline sections that have content
        String fraudSection = ctx.fraudDetectionGuidelines().isBlank() ? ""
                : "\nRELEVANT RBI/NPCI FRAUD DETECTION GUIDELINES:\n"
                + ctx.fraudDetectionGuidelines();

        String liabilitySection = ctx.customerLiabilityGuidelines().isBlank() ? ""
                : "\nCUSTOMER RIGHTS GUIDELINES:\n"
                + ctx.customerLiabilityGuidelines();

        String nextStepsSection = ctx.nextStepsGuidelines().isBlank() ? ""
                : "\nNEXT STEPS GUIDELINES:\n"
                + ctx.nextStepsGuidelines();

        return String.format("""
                You are a UPI fraud detection analyst at an Indian bank.
                A transaction has been %s with a fraud score of %.0f%%.
                Write a customer-facing explanation in EXACTLY 3 labelled sections.
                Rules: be specific, cite actual RBI/NPCI circulars, plain English,
                2-3 sentences per section, no bullet points inside sections.

                TRANSACTION:
                Ref: %s | Amount: Rs %.2f | From: %s | To: %s
                Type: %s | Device: %s | Location: %s

                FRAUD RULES TRIGGERED:
                %s
                %s%s%s

                Write EXACTLY in this format (keep the labels):

                WHY FLAGGED: [2-3 sentences — specific fraud signals citing the RBI/NPCI rule or circular]

                YOUR RIGHTS: [2-3 sentences — customer liability, zero-liability window, bank's obligation timeline]

                WHAT TO DO NOW: [2-3 sentences — call 1930, cybercrime.gov.in, bank chargeback, cms.rbi.org.in if needed]
                """,
                actionWord,
                score * 100,
                req.getTransactionRef(),
                req.getAmount().doubleValue(),
                req.getSenderVpa(),
                req.getReceiverVpa(),
                req.getTransactionType(),
                req.getDeviceId() != null ? req.getDeviceId() : "unknown",
                req.getLocation()  != null ? req.getLocation()  : "unknown",
                rulesText,
                fraudSection,
                liabilitySection,
                nextStepsSection
        );
    }

    /**
     * Rule-based fallback — produces the same 3-section format
     * using the RAG fallback strings when Gemini is unavailable.
     */
    private String buildFallbackExplanation(
            FraudCheckRequest req,
            List<RuleResult> rules,
            double score,
            String recommendation,
            RagContext ctx) {

        // WHY FLAGGED
        String whyFlagged;
        if (rules.isEmpty())
        {
            whyFlagged = String.format(
                    "This transaction of Rs %.0f was flagged by automated screening " +
                            "with a fraud score of %.0f%%. No specific rules triggered but the " +
                            "overall transaction pattern was considered unusual.",
                    req.getAmount().doubleValue(), score * 100);
        }
        else
        {
            String primary = rules.getFirst().getReason();
            String extra   = rules.size() > 1
                    ? " Additionally: " + rules.stream().skip(1)
                    .map(RuleResult::getReason)
                    .collect(Collectors.joining("; ")) + "."
                    : "";
            whyFlagged = String.format(
                    "This transaction of Rs %.0f from %s to %s was %s (fraud score %.0f%%) " +
                            "because: %s.%s",
                    req.getAmount().doubleValue(),
                    req.getSenderVpa(),
                    req.getReceiverVpa(),
                    recommendation.equals("BLOCK") ? "blocked" : "flagged",
                    score * 100,
                    primary,
                    extra);
        }

        // YOUR RIGHTS — from RAG or hardcoded fallback
        String yourRights = ctx.customerLiabilityGuidelines().isBlank()
                ? "Under RBI Customer Protection Circular 2017, you have zero liability for " +
                "unauthorized UPI transactions if reported within 3 working days. " +
                "The bank must credit the disputed amount within 10 working days."
                : ctx.customerLiabilityGuidelines();

        // WHAT TO DO NOW — from RAG or hardcoded fallback
        String whatToDo = ctx.nextStepsGuidelines().isBlank()
                ? "Call 1930 (National Cybercrime Helpline, 24x7) immediately and file a " +
                "complaint at cybercrime.gov.in. Contact your bank to raise a UPI chargeback " +
                "— under NPCI OC-198 the bank must respond within 15 business days. If " +
                "unresolved after 30 days, escalate free of charge to the RBI Ombudsman " +
                "at cms.rbi.org.in where you can claim up to Rs 20 lakh compensation."
                : ctx.nextStepsGuidelines();

        return "WHY FLAGGED: " + whyFlagged + "\n\n" +
                "YOUR RIGHTS: " + yourRights + "\n\n" +
                "WHAT TO DO NOW: " + whatToDo;
    }
}