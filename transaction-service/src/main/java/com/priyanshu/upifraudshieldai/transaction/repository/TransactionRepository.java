package com.priyanshu.upifraudshieldai.transaction.repository;

import com.priyanshu.upifraudshieldai.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID>
{

    Optional<Transaction> findByTransactionRef(String transactionRef);

    // All transactions by a user, newest first
    List<Transaction> findByUserIdOrderByCreatedAtDesc(UUID userId);

    // Recent N transactions for a user
    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId ORDER BY t.createdAt DESC LIMIT :limit")
    List<Transaction> findRecentByUserId(@Param("userId") UUID userId, @Param("limit") int limit);

    // All transactions from a VPA
    List<Transaction> findBySenderVpaOrderByCreatedAtDesc(String senderVpa);

    // Velocity check queries (used by fraud-service rules)

    // Count transactions from a VPA in last N minutes
    @Query("""
        SELECT COUNT(t) FROM Transaction t
        WHERE t.senderVpa = :vpa
        AND t.createdAt >= :since
        AND t.status NOT IN ('FAILED', 'BLOCKED')
        """)
    long countRecentByVpa(@Param("vpa") String vpa,
                          @Param("since") LocalDateTime since);

    // Total amount sent from a VPA in last N hours
    @Query("""
        SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
        WHERE t.senderVpa = :vpa
        AND t.createdAt >= :since
        AND t.status NOT IN ('FAILED', 'BLOCKED')
        """)
    BigDecimal sumAmountByVpaAndSince(@Param("vpa") String vpa,
                                      @Param("since") LocalDateTime since);

    // Count distinct receivers in last 24h (new VPA pattern detection)
    @Query("""
        SELECT COUNT(DISTINCT t.receiverVpa) FROM Transaction t
        WHERE t.senderVpa = :vpa
        AND t.createdAt >= :since
        """)
    long countDistinctReceiversSince(@Param("vpa") String vpa,
                                     @Param("since") LocalDateTime since);

    // All flagged/blocked transactions for admin dashboard
    List<Transaction> findByIsFraudTrueOrderByCreatedAtDesc();

    // Transactions by status
    List<Transaction> findByStatusOrderByCreatedAtDesc(Transaction.TransactionStatus status);

    // Check if a VPA pair has transacted before (for new-receiver detection)
    @Query("""
        SELECT COUNT(t) FROM Transaction t
        WHERE t.senderVpa = :senderVpa
        AND t.receiverVpa = :receiverVpa
        AND t.status = 'COMPLETED'
        """)

    long countCompletedBetweenVpas(@Param("senderVpa") String senderVpa,
                                   @Param("receiverVpa") String receiverVpa);
}