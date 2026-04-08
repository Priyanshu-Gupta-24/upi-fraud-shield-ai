package com.priyanshu.upifraudshieldai.fraud.kafka;

import com.priyanshu.upifraudshieldai.fraud.entity.FraudAlert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class FraudAlertPublisher
{

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.fraud-alerts}")
    private String fraudAlertsTopic;

    public void publishAlert(FraudAlert alert, String recommendation) {
        Map<String, Object> event = Map.of(
                "eventType",       "FRAUD_ALERT",
                "alertId",         alert.getId().toString(),
                "transactionId",   alert.getTransactionId().toString(),
                "transactionRef",  alert.getTransactionRef(),
                "senderVpa",       alert.getSenderVpa(),
                "receiverVpa",     alert.getReceiverVpa(),
                "amount",          alert.getAmount(),
                "fraudScore",      alert.getFraudScore(),
                "rulesTriggered",  alert.getRulesTriggered(),
                "recommendation",  recommendation
        );

        kafkaTemplate.send(fraudAlertsTopic, alert.getTransactionId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null)
                    {
                        log.error("Failed to publish fraud alert for txn {}: {}",
                                alert.getTransactionRef(), ex.getMessage());
                    }
                    else
                    {
                        log.info("Published fraud alert for txn {} → topic={}",
                                alert.getTransactionRef(), fraudAlertsTopic);
                    }
                });
    }
}