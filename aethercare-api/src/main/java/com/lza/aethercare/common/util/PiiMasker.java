package com.lza.aethercare.common.util;

/**
 * PII 遮罩工具：log / 對外輸出時用，避免 elder/contact/user id 進到
 * ELK / CloudWatch 等共用 log sink。Audit log（責任鏈）不使用此遮罩。
 *
 * <p>規則：
 * <ul>
 *   <li>{@link #maskId(Long)}：保留尾 1 位，前面以 *** 取代；{@code null} 保留為 {@code "null"}</li>
 *   <li>{@link #maskUsername(String)}：保留首末各 1 位，中間 ***</li>
 * </ul>
 */
public final class PiiMasker {

    private PiiMasker() {
    }

    /** 遮罩數字 ID。例：1001 → "***1"；null → "null"。 */
    public static String maskId(Long id) {
        if (id == null) return "null";
        String s = String.valueOf(id);
        if (s.length() <= 1) return "*";
        return "***" + s.charAt(s.length() - 1);
    }

    /** 遮罩 username。例：family01 → "f***1"；null/空 → "null"。 */
    public static String maskUsername(String username) {
        if (username == null || username.isEmpty()) return "null";
        if (username.length() <= 2) return "**";
        return username.charAt(0) + "***" + username.charAt(username.length() - 1);
    }
}
