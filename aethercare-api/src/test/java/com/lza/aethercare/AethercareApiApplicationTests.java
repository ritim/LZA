package com.lza.aethercare;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Spring context smoke test。
 * 暫 @Disabled，因為 @SpringBootTest 全載入 context 需要 PG/Redis/Kafka，
 * 改由 Batch 6 的 Testcontainers 整合測試 cover 此場景。
 */
@SpringBootTest
@Disabled("由 integration/FallDetectedEndToEndIT 用 Testcontainers cover Spring context 啟動")
class AethercareApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
