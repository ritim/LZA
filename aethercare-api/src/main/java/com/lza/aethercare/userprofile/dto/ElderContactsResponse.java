package com.lza.aethercare.userprofile.dto;

import java.util.List;

/** Spec §6.9：{ "elderId": ..., "contacts": [...] } 包裝。 */
public record ElderContactsResponse(
        Long elderId,
        List<ElderContactResponse> contacts
) {
}
