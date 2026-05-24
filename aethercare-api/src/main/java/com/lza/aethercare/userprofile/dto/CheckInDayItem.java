package com.lza.aethercare.userprofile.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Spec § Master §0：日曆 view 單日打卡狀態。
 *
 * <p>{@code status} 三態：
 * <ul>
 *   <li>{@code CHECKED_IN} — 當日已有 CHECK_IN activity（{@code checkedInAt} = 最早一筆時間）</li>
 *   <li>{@code MISSED}     — 過去日無 CHECK_IN，或今日已過 expected+grace 仍無 CHECK_IN</li>
 *   <li>{@code PENDING}    — 今日未到 expected+grace 且尚無 CHECK_IN</li>
 * </ul>
 */
public record CheckInDayItem(
        LocalDate date,
        OffsetDateTime checkedInAt,
        String status
) {
}
