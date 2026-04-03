package com.priyanshu.upifraudshieldai.transaction.kafka;

import com.priyanshu.upifraudshieldai.transaction.entity.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventPublisher
{

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.transaction-created}")
    private String transactionCreatedTopic;

    public void publish(TransactionEvent event)
    {
        kafkaTemplate.send(
                transactionCreatedTopic,
                event.getTransactionId().toString(),
                event
        ).whenComplete((result, ex) -> {
            if (ex != null)
            {
                log.error("Failed to publish event [{}] for txn [{}]: {}",
                        event.getEventType(), event.getTransactionRef(), ex.getMessage());
            }
            else
            {
                log.info("Published [{}] for txn [{}] → partition={} offset={}",
                        event.getEventType(),
                        event.getTransactionRef(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    public void publishCreated(Transaction t)
    {
        publish(TransactionEvent.builder()
                .eventType(TransactionEvent.EventType.TRANSACTION_CREATED)
                .transactionId(t.getId())
                .transactionRef(t.getTransactionRef())
                .userId(t.getUserId())
                .senderVpa(t.getSenderVpa())
                .receiverVpa(t.getReceiverVpa())
                .amount(t.getAmount())
                .status(t.getStatus())
                .build());
    }

    public void publishCompleted(Transaction t) {
        publish(buildEvent(t, TransactionEvent.EventType.TRANSACTION_COMPLETED));
    }

    public void publishFlagged(Transaction t) {
        publish(buildEvent(t, TransactionEvent.EventType.TRANSACTION_FLAGGED));
    }

    public void publishBlocked(Transaction t) {
        publish(buildEvent(t, TransactionEvent.EventType.TRANSACTION_BLOCKED));
    }

    public void publishFailed(Transaction t)
    {
        publish(buildEvent(t, TransactionEvent.EventType.TRANSACTION_FAILED));
    }

    private TransactionEvent buildEvent(Transaction t, TransactionEvent.EventType type)
    {
        return TransactionEvent.builder()
                .eventType(type)
                .transactionId(t.getId())
                .transactionRef(t.getTransactionRef())
                .userId(t.getUserId())
                .senderVpa(t.getSenderVpa())
                .receiverVpa(t.getReceiverVpa())
                .amount(t.getAmount())
                .status(t.getStatus())
                .isFraud(t.getIsFraud())
                .fraudScore(t.getFraudScore())
                .rulesTriggered(t.getRulesTriggered())
                .build();
    }
}