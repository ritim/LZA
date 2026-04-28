package com.lza.aethercare.userprofile.repository;

import com.lza.aethercare.userprofile.entity.CareContactEscalation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 升級聯絡人 repository。 */
@Repository
public interface CareContactEscalationRepository extends JpaRepository<CareContactEscalation, Long> {

    Optional<CareContactEscalation> findByElderIdAndLevelAndEnabledTrue(Long elderId, int level);

    List<CareContactEscalation> findByElderIdAndEnabledTrueOrderByLevelAsc(Long elderId);
}
