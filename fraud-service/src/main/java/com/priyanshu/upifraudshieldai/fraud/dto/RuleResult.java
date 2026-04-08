package com.priyanshu.upifraudshieldai.fraud.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleResult
{

    // Rule code e.g. RULE_HIGH_AMOUNT
    private String ruleCode;

    // Did this rule fire?
    private boolean triggered;

    // Score contribution 0.0 to 1.0
    private double scoreContribution;

    // Short reason e.g. "Amount Rs75000 exceeds threshold Rs50000"
    private String reason;
}