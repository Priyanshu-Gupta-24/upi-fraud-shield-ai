package com.priyanshu.upifraudshieldai.user.repository;

import com.priyanshu.upifraudshieldai.user.entity.UpiVpa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UpiVpaRepository extends JpaRepository<UpiVpa, UUID>
{

    Optional<UpiVpa> findByVpa(String vpa);

    List<UpiVpa> findByUserId(UUID userId);

    List<UpiVpa> findByUserIdAndStatus(UUID userId, UpiVpa.VpaStatus status);

    boolean existsByVpa(String vpa);

    Optional<UpiVpa> findByUserIdAndIsPrimaryTrue(UUID userId);
}