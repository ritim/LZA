package com.lza.aethercare.common.outbox;

/** Outbox 訊息狀態：PENDING 待送、PUBLISHED 已成功送出、DEAD_LETTER 達上限失敗。 */
public enum OutboxMessageStatus {
    PENDING,
    PUBLISHED,
    DEAD_LETTER
}
