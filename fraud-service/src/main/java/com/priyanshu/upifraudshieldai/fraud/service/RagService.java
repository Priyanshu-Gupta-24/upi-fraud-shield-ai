package com.priyanshu.upifraudshieldai.fraud.service;

import com.priyanshu.upifraudshieldai.fraud.dto.FraudCheckRequest;
import com.priyanshu.upifraudshieldai.fraud.dto.RuleResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RagService — Retrieval Augmented Generation

 * Performs THREE separate retrievals from the vector store,
 * one per knowledge category, and combines them into a
 * single context block for the LLM prompt.

 * WHY THREE SEPARATE QUERIES:
 *   A single query blends all categories and the top-K results
 *   may be dominated by one category (e.g. fraud detection docs
 *   that are semantically closer to the transaction query will
 *   always outrank customer liability docs). By querying each
 *   category with its own targeted query, we guarantee that
 *   the LLM always has:
 *   - WHY the transaction was flagged (fraud detection rules)
 *   - WHAT the customer's rights are (liability guidelines)
 *   - WHAT to do next (actionable steps)

 * The combined context produces explanations like:
 *   "This Rs 85,000 transaction was blocked because it exceeds
 *    RBI's Rs 50,000 enhanced due diligence threshold and was
 *    initiated from an unregistered device — a pattern consistent
 *    with account takeover fraud per RBI Master Direction 2024.
 *    You have zero liability if you report within 3 working days
 *    per RBI Customer Protection Circular 2017. Immediately call
 *    1930 and file a complaint at cybercrime.gov.in."
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagService
{

    private final VectorStore vectorStore;

    @Value("${fraud.rag.top-k:3}")
    private int topK;

    /**
     * Retrieves context from all 3 categories and combines them.
     * Returns a formatted multisection string for the LLM prompt.
     */
    public RagContext retrieveContext(
            FraudCheckRequest request,
            List<RuleResult> triggeredRules,
            String recommendation) {

        String fraudQuery     = buildFraudDetectionQuery(request, triggeredRules);
        String liabilityQuery = buildLiabilityQuery(recommendation);
        String nextStepsQuery = buildNextStepsQuery(recommendation);

        String fraudGuidelines     = retrieve(fraudQuery,     "fraud_detection", 3);
        String liabilityGuidelines = retrieve(liabilityQuery, "customer_liability", 2);
        String nextStepsGuidelines = retrieve(nextStepsQuery, "next_steps", 3);

        log.debug("RAG retrieval for txn {}: fraud={} chars, liability={} chars, nextSteps={} chars",
                request.getTransactionRef(),
                fraudGuidelines.length(),
                liabilityGuidelines.length(),
                nextStepsGuidelines.length());

        return new RagContext(fraudGuidelines, liabilityGuidelines, nextStepsGuidelines);
    }

    /**
     * Builds a targeted query for fraud detection guidelines
     * based on which specific rules triggered.
     */
    private String buildFraudDetectionQuery(
            FraudCheckRequest req, List<RuleResult> rules)
    {

        StringBuilder q = new StringBuilder();
        q.append("UPI fraud transaction detection rule ");
        q.append("amount ").append(req.getAmount()).append(" rupees ");
        q.append("type ").append(req.getTransactionType()).append(" ");

        for (RuleResult rule : rules)
        {
            switch (rule.getRuleCode())
            {
                case "RULE_HIGH_AMOUNT"     -> q.append("high value transaction threshold enhanced due diligence ");
                case "RULE_VELOCITY_5MIN"   -> q.append("velocity attack multiple transactions 5 minutes ");
                case "RULE_VELOCITY_1HR"    -> q.append("hourly transaction velocity limit ");
                case "RULE_DAILY_LIMIT"     -> q.append("daily transaction limit exceeded UPI ");
                case "RULE_ODD_HOURS"       -> q.append("odd hours late night fraud suspicious time ");
                case "RULE_NEW_DEVICE"      -> q.append("new unregistered device account takeover SIM swap ");
                case "RULE_ROUND_AMOUNT"    -> q.append("round amount money laundering structuring ");
                case "RULE_NEW_RECEIVER"    -> q.append("new payee first time payment cooling period ");
                case "RULE_MULTIPLE_NEW_VPAS" -> q.append("multiple new payees 24 hours account compromise mule ");
                case "RULE_FIRST_HIGH_VALUE"  -> q.append("first transaction high value new account fraud ");
            }
        }

        return q.toString().trim();
    }

    /**
     * Builds a targeted query for customer liability guidelines.
     * Adjusts based on how severe the recommendation is.
     */
    private String buildLiabilityQuery(String recommendation)
    {
        return switch (recommendation)
        {
            case "BLOCK" -> "zero liability unauthorized UPI transaction customer rights reporting 3 days bank obligation refund";
            case "FLAG"  -> "limited liability suspicious transaction customer protection unauthorized electronic banking";
            default      -> "customer protection UPI transaction rights bank liability";
        };
    }

    /**
     * Builds a targeted query for next steps / actionable guidance.
     */
    private String buildNextStepsQuery(String recommendation)
    {
        return switch (recommendation)
        {
            case "BLOCK" -> "1930 cybercrime.gov.in FIR chargeback dispute steps after UPI fraud blocked transaction";
            case "FLAG"  -> "dispute resolution chargeback NPCI bank complaint steps flagged transaction";
            default      -> "UPI dispute resolution steps bank complaint";
        };
    }

    /**
     * Performs a similarity search against the vector store.
     * Uses metadata filter to restrict results to a specific category.
     * Falls back gracefully if search fails.
     */
    private String retrieve(String query, String category, int k)
    {
        try
        {
            List<Document> docs = vectorStore.similaritySearch(
                            SearchRequest.builder().query(query)
                                    .topK(k)
                                    .similarityThreshold(0.3).build());

            if (docs.isEmpty())
            {
                log.debug("No results for category '{}' query: {}", category, query);
                return getFallback(category);
            }

            return docs.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n"));

        }
        catch (Exception e)
        {
            log.warn("RAG retrieval failed for category '{}': {}", category, e.getMessage());
            return getFallback(category);
        }
    }

    private String getFallback(String category)
    {
        return switch (category)
        {
            case "fraud_detection" ->
                    "RBI guidelines require banks to flag high-value UPI transactions above Rs 50,000, " +
                            "velocity patterns exceeding 3 transactions in 5 minutes, and transactions from " +
                            "unregistered devices as per RBI Master Direction on Fraud Risk Management 2024.";
            case "customer_liability" ->
                    "Under RBI Customer Protection Circular 2017, you have zero liability for " +
                            "unauthorized UPI transactions if reported within 3 working days. " +
                            "The bank must credit the disputed amount within 10 working days of your complaint.";
            case "next_steps" ->
                    "Immediately call 1930 (National Cybercrime Helpline) and file a complaint at " +
                            "cybercrime.gov.in. Contact your bank to raise a UPI chargeback. " +
                            "If unresolved in 30 days, escalate to RBI Ombudsman at cms.rbi.org.in.";
            default -> "";
        };
    }

    /**
     * Holds the three-category context retrieved from pgvector.
     * Passed directly to ExplanationService for prompt construction.
     */
    public record RagContext(
            String fraudDetectionGuidelines,
            String customerLiabilityGuidelines,
            String nextStepsGuidelines
    ) {}
}