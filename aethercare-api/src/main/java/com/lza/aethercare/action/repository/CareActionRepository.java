package com.lza.aethercare.action.repository;

import com.lza.aethercare.action.entity.CareAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 照護動作 repository。 */
@Repository
public interface CareActionRepository extends JpaRepository<CareAction, Long> {

    List<CareAction> findByWorkflowIdOrderByCreatedAtAsc(Long workflowId);
}
