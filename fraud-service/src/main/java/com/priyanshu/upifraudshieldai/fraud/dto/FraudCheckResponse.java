package com.priyanshu.upifraudshieldai.fraud.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FraudCheckResponse
{

    // Is this transaction fraudulent?
    private boolean fraud;

    // Confidence score 0.0 (clean) to 1.0 (definite fraud)
    private double fraudScore;

    // List of rule codes that fired e.g. ["RULE_HIGH_AMOUNT", "RULE_NEW_DEVICE"]
    private List<String> triggeredRules;

    // Natural language explanation from LLM
    private String explanation;

    // ALLOW, FLAG, or BLOCK
    private String recommendation;
}