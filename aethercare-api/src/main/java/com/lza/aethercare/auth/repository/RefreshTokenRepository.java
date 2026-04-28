package com.lza.aethercare.auth.repository;

import com.lza.aethercare.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

/** Refresh token repository：以 token_hash 查找 + native UPDATE/DELETE 控制併發。 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** 標記單一 token revoked + 設 replaced_by_id。回傳影響筆數（0 = 已被別人 revoke）。 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE refresh_token
           SET revoked = true, revoked_at = :now, replaced_by_id = :replacedBy
         WHERE id = :id AND revoked = false
        """, nativeQuery = true)
    int revokeIfActive(@Param("id") Long id,
                       @Param("now") OffsetDateTime now,
                       @Param("replacedBy") Long replacedBy);

    /** Reuse detection：撤銷該 user 所有 active refresh token。 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE refresh_token
           SET revoked = true, revoked_at = :now
         WHERE user_id = :userId AND revoked = false
        """, nativeQuery = true)
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") OffsetDateTime now);

    /**
     * 批次刪 expired 或已 revoked 超過保留期的 token；
     * PG 不支援 DELETE LIMIT，改用子查詢限制每次最多 :batchSize 筆。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        DELETE FROM refresh_token
         WHERE id IN (
            SELECT id FROM refresh_token
             WHERE (expires_at < :now)
                OR (revoked = true AND revoked_at < :revokedCutoff)
             LIMIT :batchSize
         )
        """, nativeQuery = true)
    int deleteExpiredOrOldRevoked(@Param("now") OffsetDateTime now,
                                  @Param("revokedCutoff") OffsetDateTime revokedCutoff,
                                  @Param("batchSize") int batchSize);
}
