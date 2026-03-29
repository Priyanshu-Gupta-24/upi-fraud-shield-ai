package com.priyanshu.upifraudshieldai.user.kafka;

import com.priyanshu.upifraudshieldai.user.entity.UpiVpa;
import com.priyanshu.upifraudshieldai.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventPublisher
{

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.user-events}")
    private String userEventsTopic;

    // Publish any UserEvent
    public void publish(UserEvent event)
    {
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(userEventsTopic, event.getUserId().toString(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null)
            {
                log.error("Failed to publish event [{}] for user [{}]: {}",
                        event.getEventType(), event.getUserId(), ex.getMessage());
            }
            else
            {
                log.info("Published event [{}] for user [{}] → partition={} offset={}",
                        event.getEventType(),
                        event.getUserId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    public void publishUserRegistered(User user)
    {
        publish(UserEvent.builder()
                .eventType(UserEvent.EventType.USER_REGISTERED)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .deviceId(user.getDeviceId())
                .build());
    }

    public void publishUserLoggedIn(User user)
    {
        publish(UserEvent.builder()
                .eventType(UserEvent.EventType.USER_LOGGED_IN)
                .userId(user.getId())
                .username(user.getUsername())
                .deviceId(user.getDeviceId())
                .build());
    }

    public void publishUserUpdated(User user)
    {
        publish(UserEvent.builder()
                .eventType(UserEvent.EventType.USER_UPDATED)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .build());
    }

    public void publishStatusChanged(User user)
    {
        publish(UserEvent.builder()
                .eventType(UserEvent.EventType.USER_STATUS_CHANGED)
                .userId(user.getId())
                .username(user.getUsername())
                .metadata("{\"newStatus\":\"" + user.getStatus().name() + "\"}")
                .build());
    }

    public void publishUserLocked(User user)
    {
        publish(UserEvent.builder()
                .eventType(UserEvent.EventType.USER_LOCKED)
                .userId(user.getId())
                .username(user.getUsername())
                .metadata("{\"lockedUntil\":\"" + user.getLockedUntil() + "\"}")
                .build());
    }

    public void publishVpaAdded(User user, UpiVpa vpa)
    {
        publish(UserEvent.builder()
                .eventType(UserEvent.EventType.VPA_ADDED)
                .userId(user.getId())
                .username(user.getUsername())
                .metadata("{\"vpa\":\"" + vpa.getVpa() + "\"}")
                .build());
    }

    public void publishPasswordChanged(User user)
    {
        publish(UserEvent.builder()
                .eventType(UserEvent.EventType.PASSWORD_CHANGED)
                .userId(user.getId())
                .username(user.getUsername())
                .build());
    }
}