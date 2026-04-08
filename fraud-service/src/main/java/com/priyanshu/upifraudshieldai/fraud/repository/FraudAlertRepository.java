package com.priyanshu.upifraudshieldai.fraud.repository;

import com.priyanshu.upifraudshieldai.fraud.entity.FraudAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FraudAlertRepository extends JpaRepository<FraudAlert, UUID> {

    Optional<FraudAlert> findByTransactionId(UUID transactionId);

    List<FraudAlert> findByStatusOrderByCreatedAtDesc(FraudAlert.AlertStatus status);

    List<FraudAlert> findBySenderVpaOrderByCreatedAtDesc(String senderVpa);

    List<FraudAlert> findAllByOrderByCreatedAtDesc();

    long countByStatus(FraudAlert.AlertStatus status);
}