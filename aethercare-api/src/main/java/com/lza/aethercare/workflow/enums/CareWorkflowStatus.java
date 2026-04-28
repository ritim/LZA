package com.lza.aethercare.workflow.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Workflow 狀態機：集中管理允許的轉移。 */
public enum CareWorkflowStatus {
    NEW,
    ACTIVE,
    WAITING_RESPONSE,
    ACKNOWLEDGED,
    ESCALATED,
    RESOLVED,
    UNRESOLVED;

    private static final Map<CareWorkflowStatus, Set<CareWorkflowStatus>> TRANSITIONS = Map.of(
            NEW, EnumSet.of(ACTIVE, UNRESOLVED),
            ACTIVE, EnumSet.of(WAITING_RESPONSE, RESOLVED, UNRESOLVED),
            WAITING_RESPONSE, EnumSet.of(ACKNOWLEDGED, ESCALATED, RESOLVED, UNRESOLVED),
            ACKNOWLEDGED, EnumSet.of(RESOLVED, ESCALATED, UNRESOLVED),
            ESCALATED, EnumSet.of(WAITING_RESPONSE, RESOLVED, UNRESOLVED),
            RESOLVED, EnumSet.noneOf(CareWorkflowStatus.class),
            UNRESOLVED, EnumSet.noneOf(CareWorkflowStatus.class)
    );

    /** 是否允許轉移到指定狀態。 */
    public boolean canTransitionTo(CareWorkflowStatus next) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(next);
    }

    /** 是否為終態。 */
    public boolean isTerminal() {
        return TRANSITIONS.getOrDefault(this, Set.of()).isEmpty();
    }
}
