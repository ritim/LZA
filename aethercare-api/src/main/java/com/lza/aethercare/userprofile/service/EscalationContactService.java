package com.lza.aethercare.userprofile.service;

import com.lza.aethercare.userprofile.entity.CareContactEscalation;
import com.lza.aethercare.userprofile.repository.CareContactEscalationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** 升級聯絡人查詢 service。 */
@Service
@RequiredArgsConstructor
public class EscalationContactService {

    private final CareContactEscalationRepository repo;

    @Transactional(readOnly = true)
    public Optional<CareContactEscalation> findContact(Long elderId, int level) {
        return repo.findByElderIdAndLevelAndEnabledTrue(elderId, level);
    }
}
