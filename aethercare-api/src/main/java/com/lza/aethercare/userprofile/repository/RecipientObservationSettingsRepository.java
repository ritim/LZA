package com.lza.aethercare.userprofile.repository;

import com.lza.aethercare.userprofile.entity.RecipientObservationSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 觀察設定 repository：以 care_recipient_id 唯一索引查找。 */
@Repository
public interface RecipientObservationSettingsRepository extends JpaRepository<RecipientObservationSettings, Long> {

    Optional<RecipientObservationSettings> findByCareRecipientId(Long careRecipientId);

    /** Missed-checkin scanner 用：找出所有開啟被動監測且設了預期 check-in 時間的設定。 */
    List<RecipientObservationSettings> findAllByPassiveMonitoringEnabledTrueAndExpectedCheckinTimeNotNull();
}
