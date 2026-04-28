package com.lza.aethercare.anomaly.repository;

import com.lza.aethercare.anomaly.entity.ElderActivityBaseline;
import com.lza.aethercare.anomaly.enums.ActivityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 活動 baseline repository。 */
@Repository
public interface ElderActivityBaselineRepository extends JpaRepository<ElderActivityBaseline, Long> {

    Optional<ElderActivityBaseline> findByElderIdAndActivityTypeAndHourOfDay(Long elderId,
                                                                              ActivityType activityType,
                                                                              int hourOfDay);

    List<ElderActivityBaseline> findByElderIdAndActivityType(Long elderId, ActivityType activityType);
}
