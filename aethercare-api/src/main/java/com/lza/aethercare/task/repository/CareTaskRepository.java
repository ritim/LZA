package com.lza.aethercare.task.repository;

import com.lza.aethercare.task.entity.CareTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/** 照護任務 repository：對應系統設計 §13.1 / §13.2 conditional update。 */
@Repository
public interface CareTaskRepository extends JpaRepository<CareTask, Long> {

    @Query(value = """
        SELECT * FROM care_task
         WHERE status = 'PENDING' AND deadline_at < :now
        """, nativeQuery = true)
    List<CareTask> findExpiredPendingTasks(@Param("now") OffsetDateTime now);

    /** 文件 §13.1：防止重複 timeout。 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE care_task
           SET status = 'TIMEOUT', updated_at = :now, version = version + 1
         WHERE id = :id AND status = 'PENDING' AND deadline_at < :now
        """, nativeQuery = true)
    int markTimeoutIfPending(@Param("id") Long id, @Param("now") OffsetDateTime now);

    /** 文件 §13.2：防止重複完成。 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE care_task
           SET status = 'COMPLETED', completed_at = :now, updated_at = :now, version = version + 1
         WHERE id = :id AND status IN ('PENDING','ACKNOWLEDGED')
        """, nativeQuery = true)
    int completeIfActive(@Param("id") Long id, @Param("now") OffsetDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE care_task
           SET status = 'ACKNOWLEDGED', acknowledged_at = :now, updated_at = :now, version = version + 1
         WHERE id = :id AND status = 'PENDING'
        """, nativeQuery = true)
    int acknowledgeIfPending(@Param("id") Long id, @Param("now") OffsetDateTime now);

    List<CareTask> findByWorkflowIdOrderByLevelAscIdAsc(Long workflowId);

    /** Dashboard 用：caregiver 名下尚未結案的任務（PENDING / ACKNOWLEDGED）。 */
    @Query(value = """
        SELECT * FROM care_task
         WHERE assignee_id = :assigneeId
           AND status IN ('PENDING','ACKNOWLEDGED')
         ORDER BY deadline_at ASC
        """, nativeQuery = true)
    List<CareTask> findActiveByAssignee(@Param("assigneeId") Long assigneeId);
}
