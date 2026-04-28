package com.lza.aethercare.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

/** Outbox 訊息 repository：scheduler 用 native query 撈 / 標記，避免 JPA cache 造成 race。 */
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

    @Query(value = """
        SELECT * FROM outbox_message
         WHERE status = 'PENDING'
           AND next_attempt_at <= :now
         ORDER BY next_attempt_at ASC
         LIMIT :batchSize
        """, nativeQuery = true)
    List<OutboxMessage> findReadyToSend(@Param("now") OffsetDateTime now,
                                        @Param("batchSize") int batchSize);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE outbox_message
           SET status = 'PUBLISHED', sent_at = :now, version = version + 1
         WHERE id = :id AND status = 'PENDING'
        """, nativeQuery = true)
    int markPublished(@Param("id") Long id, @Param("now") OffsetDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE outbox_message
           SET attempt_count = attempt_count + 1,
               next_attempt_at = :nextAttemptAt,
               last_error = :error,
               version = version + 1
         WHERE id = :id AND status = 'PENDING'
        """, nativeQuery = true)
    int markRetryScheduled(@Param("id") Long id,
                           @Param("nextAttemptAt") OffsetDateTime nextAttemptAt,
                           @Param("error") String error);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE outbox_message
           SET status = 'DEAD_LETTER', last_error = :error, version = version + 1
         WHERE id = :id AND status = 'PENDING'
        """, nativeQuery = true)
    int markDeadLetter(@Param("id") Long id, @Param("error") String error);
}
