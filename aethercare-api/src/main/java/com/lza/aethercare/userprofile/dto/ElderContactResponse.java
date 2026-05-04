package com.lza.aethercare.userprofile.dto;

/** Spec §6.9 contacts payload single item。 */
public record ElderContactResponse(
        Long id,
        String name,
        String relationship,
        String phone,
        int priorityLevel
) {
}
