package com.lza.aethercare.anomaly.repository;

import com.lza.aethercare.anomaly.entity.ElderActivityEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 長者活動事件 repository。 */
@Repository
public interface ElderActivityEventRepository extends JpaRepository<ElderActivityEvent, Long> {

    @Query(value = """
            SELECT COUNT(*) FROM elder_activity_event
             WHERE elder_id = :elderId
               AND activity_type = :activityType
               AND occurred_at >= :from AND occurred_at < :to
            """, nativeQuery = true)
    long countInWindow(@Param("elderId") Long elderId,
                       @Param("activityType") String activityType,
                       @Param("from") OffsetDateTime from,
                       @Param("to") OffsetDateTime to);

    @Query(value = """
            SELECT
              EXTRACT(HOUR FROM occurred_at AT TIME ZONE 'UTC') AS hour_of_day,
              DATE(occurred_at AT TIME ZONE 'UTC') AS day,
              COUNT(*) AS event_count
            FROM elder_activity_event
             WHERE elder_id = :elderId
               AND activity_type = :activityType
               AND occurred_at >= :from
             GROUP BY hour_of_day, day
            """, nativeQuery = true)
    List<Object[]> aggregateHourlyCountsSince(@Param("elderId") Long elderId,
                                              @Param("activityType") String activityType,
                                              @Param("from") OffsetDateTime from);

    @Query(value = "SELECT DISTINCT elder_id FROM elder_activity_event", nativeQuery = true)
    List<Long> findDistinctElderIds();

    /** Multi-signal fusion 用：取窗口內某 elder 全部 activity events（含 metadata）。 */
    @Query(value = """
            SELECT * FROM elder_activity_event
             WHERE elder_id = :elderId
               AND occurred_at >= :from AND occurred_at < :to
            """, nativeQuery = true)
    List<ElderActivityEvent> findByElderIdAndOccurredAtBetween(@Param("elderId") Long elderId,
                                                               @Param("from") OffsetDateTime from,
                                                               @Param("to") OffsetDateTime to);

    /** No-activity scanner 用：取此 elder 最近一次活動，沒有任何上報則 empty。 */
    Optional<ElderActivityEvent> findFirstByElderIdOrderByOccurredAtDesc(Long elderId);

    /** 取窗口內某 elder 指定 activity type 的活動（依 occurred_at 升冪），給日曆 view 聚合用。 */
    @Query(value = """
            SELECT * FROM elder_activity_event
             WHERE elder_id = :elderId
               AND activity_type = :activityType
               AND occurred_at >= :from AND occurred_at < :to
             ORDER BY occurred_at ASC
            """, nativeQuery = true)
    List<ElderActivityEvent> findByElderIdAndTypeAndOccurredAtBetween(
            @Param("elderId") Long elderId,
            @Param("activityType") String activityType,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    /** Dashboard latestCheckInAt：在指定 elder 集合裡找最近一筆指定型別活動。 */
    @Query(value = """
        SELECT * FROM elder_activity_event
         WHERE elder_id IN (:elderIds)
           AND activity_type = :activityType
         ORDER BY occurred_at DESC
         LIMIT 1
        """, nativeQuery = true)
    Optional<ElderActivityEvent> findLatestByElderIdsAndType(
            @Param("elderIds") Collection<Long> elderIds,
            @Param("activityType") String activityType);

    /** Dashboard latestActivityAt：在指定 elder 集合裡找最近一筆任意活動。 */
    @Query(value = """
        SELECT * FROM elder_activity_event
         WHERE elder_id IN (:elderIds)
         ORDER BY occurred_at DESC
         LIMIT 1
        """, nativeQuery = true)
    Optional<ElderActivityEvent> findLatestByElderIds(@Param("elderIds") Collection<Long> elderIds);
}
