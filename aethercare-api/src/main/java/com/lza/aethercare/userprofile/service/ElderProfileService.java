package com.lza.aethercare.userprofile.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lza.aethercare.common.error.BusinessException;
import com.lza.aethercare.common.error.ErrorCode;
import com.lza.aethercare.event.entity.CareEvent;
import com.lza.aethercare.event.repository.CareEventRepository;
import com.lza.aethercare.userprofile.dto.ElderContactResponse;
import com.lza.aethercare.userprofile.dto.ElderContactsResponse;
import com.lza.aethercare.userprofile.dto.ElderEventItem;
import com.lza.aethercare.userprofile.dto.ElderProfileResponse;
import com.lza.aethercare.userprofile.entity.ElderContact;
import com.lza.aethercare.userprofile.entity.ElderProfile;
import com.lza.aethercare.userprofile.repository.ElderContactRepository;
import com.lza.aethercare.userprofile.repository.ElderProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/** Elder 資料 read-only service。chronic_diseases / allergies JSON 欄位反序列化在此處理。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ElderProfileService {

    private final ElderProfileRepository profileRepo;
    private final ElderContactRepository contactRepo;
    private final CareEventRepository eventRepo;
    private final ObjectMapper objectMapper;

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    @Transactional(readOnly = true)
    public ElderProfileResponse getProfile(Long elderId) {
        ElderProfile p = profileRepo.findById(elderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "elder " + elderId));
        return new ElderProfileResponse(
                p.getId(),
                p.getName(),
                p.getAge(),
                p.getGender(),
                p.getMobility(),
                parseStringList(p.getChronicDiseases()),
                parseStringList(p.getAllergies()),
                p.getAddress(),
                p.getEmergencyNotes()
        );
    }

    @Transactional(readOnly = true)
    public ElderContactsResponse getContacts(Long elderId) {
        if (!profileRepo.existsById(elderId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "elder " + elderId);
        }
        List<ElderContactResponse> contacts = contactRepo
                .findByElderIdOrderByPriorityLevelAsc(elderId)
                .stream()
                .map(this::toContact)
                .toList();
        return new ElderContactsResponse(elderId, contacts);
    }

    @Transactional(readOnly = true)
    public List<ElderEventItem> getRecentEvents(Long elderId, int limit) {
        if (!profileRepo.existsById(elderId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "elder " + elderId);
        }
        return eventRepo.findByElderIdOrderByOccurredAtDesc(elderId)
                .stream()
                .limit(Math.max(1, limit))
                .map(this::toEventItem)
                .toList();
    }

    private ElderContactResponse toContact(ElderContact c) {
        return new ElderContactResponse(
                c.getId(),
                c.getName(),
                c.getRelationship(),
                c.getPhone(),
                c.getPriorityLevel()
        );
    }

    private ElderEventItem toEventItem(CareEvent e) {
        return new ElderEventItem(
                e.getId(),
                e.getEventType(),
                e.getRiskLevel(),
                e.getStatus(),
                e.getOccurredAt()
        );
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (IOException ex) {
            log.warn("反序列化 elder JSON list 失敗 raw={}", json, ex);
            return Collections.emptyList();
        }
    }
}
