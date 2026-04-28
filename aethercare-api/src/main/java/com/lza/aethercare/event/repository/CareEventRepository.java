package com.lza.aethercare.event.repository;

import com.lza.aethercare.event.entity.CareEvent;
import com.lza.aethercare.event.enums.CareEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 照護事件 repository。 */
@Repository
public interface CareEventRepository extends JpaRepository<CareEvent, Long> {

    Optional<CareEvent> findByIdAndStatus(Long id, CareEventStatus status);
}
