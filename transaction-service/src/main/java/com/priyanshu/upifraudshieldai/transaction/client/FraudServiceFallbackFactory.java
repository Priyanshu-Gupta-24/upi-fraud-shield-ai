package com.priyanshu.upifraudshieldai.transaction.client;

import com.priyanshu.upifraudshieldai.transaction.dto.FraudCheckResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class FraudServiceFallbackFactory implements FallbackFactory<FraudServiceClient>
{

    @Override
    public FraudServiceClient create(Throwable cause)
    {
        log.warn("Fraud service fallback triggered. Cause: {}", cause.getMessage());

        return request ->
        {
            log.warn("Fallback: returning ALLOW for txn [{}]. Reason: {}",
                    request.getTransactionRef(), cause.getMessage());

            return FraudCheckResponse.builder()
                    .fraud(false)
                    .fraudScore(0.0)
                    .triggeredRules(List.of())
                    .recommendation("ALLOW")
                    .explanation("Fraud check service temporarily unavailable. " +
                            "Transaction allowed by default.Kindly review manually.")
                    .build();
        };
    }
}