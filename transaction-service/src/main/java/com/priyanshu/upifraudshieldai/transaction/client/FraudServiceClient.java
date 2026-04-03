package com.priyanshu.upifraudshieldai.transaction.client;

import com.priyanshu.upifraudshieldai.transaction.dto.FraudCheckRequest;
import com.priyanshu.upifraudshieldai.transaction.dto.FraudCheckResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "fraud-service",
        fallbackFactory = FraudServiceFallbackFactory.class
)
public interface FraudServiceClient
{
    @PostMapping("/api/fraud/check")
    FraudCheckResponse checkFraud(@RequestBody FraudCheckRequest request);
}