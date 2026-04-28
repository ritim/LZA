package com.lza.aethercare.audit.repository;

import com.lza.aethercare.audit.entity.CareAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Audit log repository：append-only 時序查詢。 */
@Repository
public interface CareAuditLogRepository extends JpaRepository<CareAuditLog, Long> {

    List<CareAuditLog> findByWorkflowIdOrderByCreatedAtAsc(Long workflowId);
}
