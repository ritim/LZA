package com.lza.aethercare.event.repository;

import com.lza.aethercare.event.entity.CareEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** 照護事件 repository。 */
@Repository
public interface CareEventRepository extends JpaRepository<CareEvent, Long> {
}
