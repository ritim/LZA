package com.lza.aethercare.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** PiiMasker 單元測試：驗證 ID / username 遮罩規則與邊界。 */
class PiiMaskerTest {

    @Test
    void should_mask_id_keep_last_digit() {
        assertThat(PiiMasker.maskId(1001L)).isEqualTo("***1");
        assertThat(PiiMasker.maskId(2002L)).isEqualTo("***2");
        assertThat(PiiMasker.maskId(99L)).isEqualTo("***9");
    }

    @Test
    void should_handle_null_id() {
        assertThat(PiiMasker.maskId(null)).isEqualTo("null");
    }

    @Test
    void should_handle_single_digit_id() {
        assertThat(PiiMasker.maskId(7L)).isEqualTo("*");
    }

    @Test
    void should_mask_username_keep_first_and_last() {
        assertThat(PiiMasker.maskUsername("family01")).isEqualTo("f***1");
        assertThat(PiiMasker.maskUsername("admin")).isEqualTo("a***n");
    }

    @Test
    void should_handle_short_or_null_username() {
        assertThat(PiiMasker.maskUsername(null)).isEqualTo("null");
        assertThat(PiiMasker.maskUsername("")).isEqualTo("null");
        assertThat(PiiMasker.maskUsername("ab")).isEqualTo("**");
    }
}
