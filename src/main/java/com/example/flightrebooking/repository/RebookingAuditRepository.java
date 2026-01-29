package com.example.flightrebooking.repository;

import com.example.flightrebooking.entity.RebookingAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RebookingAuditRepository extends JpaRepository<RebookingAudit, UUID> {

    Optional<RebookingAudit> findByIdempotencyKey(UUID idempotencyKey);
}
