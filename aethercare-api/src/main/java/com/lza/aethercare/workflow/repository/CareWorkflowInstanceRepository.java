package com.lza.aethercare.workflow.repository;

import com.lza.aethercare.workflow.entity.CareWorkflowInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;

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

    /** GET /api/v1/care-events/{id} 用：取對應最新一筆 workflow（單一事件預期僅一筆）。 */
    Optional<CareWorkflowInstance> findFirstByEventIdOrderByIdDesc(Long eventId);

    /** Dashboard 用：今日已結案 workflow 數量（spec § Gap H resolvedTodayCount）。 */
    @Query(value = """
        SELECT COUNT(*) FROM care_workflow_instance
         WHERE status = 'RESOLVED' AND completed_at >= :startOfDay
        """, nativeQuery = true)
    long countResolvedSince(@Param("startOfDay") OffsetDateTime startOfDay);
}
