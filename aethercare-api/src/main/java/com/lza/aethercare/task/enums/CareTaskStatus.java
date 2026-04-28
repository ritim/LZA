package com.lza.aethercare.task.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** 照護任務狀態機：集中管理允許的轉移。 */
public enum CareTaskStatus {
    PENDING,
    ACKNOWLEDGED,
    COMPLETED,
    TIMEOUT,
    CANCELLED;

    private static final Map<CareTaskStatus, Set<CareTaskStatus>> TRANSITIONS = Map.of(
            PENDING, EnumSet.of(ACKNOWLEDGED, COMPLETED, TIMEOUT, CANCELLED),
            ACKNOWLEDGED, EnumSet.of(COMPLETED, TIMEOUT, CANCELLED),
            COMPLETED, EnumSet.noneOf(CareTaskStatus.class),
            TIMEOUT, EnumSet.noneOf(CareTaskStatus.class),
            CANCELLED, EnumSet.noneOf(CareTaskStatus.class)
    );

    /** 是否允許轉移到指定狀態。 */
    public boolean canTransitionTo(CareTaskStatus next) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(next);
    }

    /** 是否為終態。 */
    public boolean isTerminal() {
        return TRANSITIONS.getOrDefault(this, Set.of()).isEmpty();
    }
}
