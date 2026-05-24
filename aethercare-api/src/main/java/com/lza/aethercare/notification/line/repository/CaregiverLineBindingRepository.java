package com.lza.aethercare.notification.line.repository;

import com.lza.aethercare.notification.line.entity.CaregiverLineBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CaregiverLineBindingRepository extends JpaRepository<CaregiverLineBinding, Long> {

    Optional<CaregiverLineBinding> findByCaregiverId(Long caregiverId);

    Optional<CaregiverLineBinding> findByLineUserId(String lineUserId);

    List<CaregiverLineBinding> findByCaregiverIdIn(List<Long> caregiverIds);

    void deleteByCaregiverId(Long caregiverId);
}
