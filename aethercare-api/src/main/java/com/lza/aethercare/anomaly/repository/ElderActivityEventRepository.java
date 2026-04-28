package com.lza.aethercare.anomaly.repository;

import com.lza.aethercare.anomaly.entity.ElderActivityEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

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
}
