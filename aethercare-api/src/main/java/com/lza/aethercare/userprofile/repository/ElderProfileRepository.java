package com.lza.aethercare.userprofile.repository;

import com.lza.aethercare.userprofile.entity.ElderProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** 長者基本資料 repository。 */
@Repository
public interface ElderProfileRepository extends JpaRepository<ElderProfile, Long> {
}
