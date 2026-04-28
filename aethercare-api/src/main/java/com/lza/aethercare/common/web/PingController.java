package com.lza.aethercare.common.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/** 簡易 ping endpoint：公開可讀，給前端 / 監控腳本確認 API 啟動。 */
@RestController
@RequestMapping("/api/v1")
public class PingController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "service", "aethercare-api",
                "status", "ok",
                "timestamp", Instant.now().toString()
        );
    }
}
