package com.lza.aethercare.userprofile.dto;

import java.util.List;

/** Spec §6.3 GET /api/elders/{id} 回應。 */
public record ElderProfileResponse(
        Long id,
        String name,
        int age,
        String gender,
        String mobility,
        List<String> chronicDiseases,
        List<String> allergies,
        String address,
        String emergencyNotes
) {
}
