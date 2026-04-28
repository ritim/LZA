package com.lza.aethercare.workflow.repository;

import com.lza.aethercare.workflow.entity.CareWorkflowInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;

/** 照護流程 repository：所有 conditional update 走 native SQL（plan §8.2）。 */
@Repository
public interface CareWorkflowInstanceRepository extends JpaRepository<CareWorkflowInstance, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE care_workflow_instance
           SET status = :nextStatus, completed_at = :now, updated_at = :now, version = version + 1
         WHERE id = :id AND status IN (:allowedStatuses)
        """, nativeQuery = true)
    int markTerminalIfIn(@Param("id") Long id,
                         @Param("nextStatus") String nextStatus,
                         @Param("allowedStatuses") Collection<String> allowedStatuses,
                         @Param("now") OffsetDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE care_workflow_instance
           SET status = :nextStatus, current_level = :level, updated_at = :now, version = version + 1
         WHERE id = :id AND status IN (:allowedStatuses)
        """, nativeQuery = true)
    int advanceLevel(@Param("id") Long id,
                     @Param("level") int level,
                     @Param("nextStatus") String nextStatus,
                     @Param("allowedStatuses") Collection<String> allowedStatuses,
                     @Param("now") OffsetDateTime now);
}
