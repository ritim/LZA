package com.lza.aethercare.userprofile.repository;

import com.lza.aethercare.userprofile.entity.ElderContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 長者聯絡人 repository。 */
@Repository
public interface ElderContactRepository extends JpaRepository<ElderContact, Long> {

    List<ElderContact> findByElderIdOrderByPriorityLevelAsc(Long elderId);
}
