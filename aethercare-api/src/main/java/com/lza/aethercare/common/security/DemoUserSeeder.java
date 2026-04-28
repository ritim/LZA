package com.lza.aethercare.common.security;

import com.lza.aethercare.common.util.PiiMasker;
import com.lza.aethercare.userprofile.entity.AppUser;
import com.lza.aethercare.userprofile.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * Demo 帳號 seeder：application ready 後若 username 不存在則插入。
 * 使用 PasswordEncoder.encode 即時計算 BCrypt hash，避免 hardcode。
 */
@Component
@ConditionalOnProperty(name = "aethercare.security.seed-demo-users", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DemoUserSeeder {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        upsert("admin", "admin123", "管理者", Set.of("USER", "ADMIN"), 1L);
        upsert("family01", "family123", "家屬一號（level 1）", Set.of("USER"), 1L);
        upsert("family02", "family123", "家屬二號（level 2）", Set.of("USER"), 1L);
        upsert("insurer01", "insurer123", "保險業者代表", Set.of("INSURANCE"), 1L);
        // Premium tenant demo（隔離驗證用）；對應系統設計 §20 Phase 2 multi-tenant framework
        upsert("premium-family01", "premium-family123", "Premium 家屬一號",
                Set.of("USER"), 2L);
    }

    private void upsert(String username, String rawPassword, String displayName, Set<String> roles, Long tenantId) {
        if (appUserRepository.existsByUsername(username)) {
            log.debug("demo user 已存在，略過 seed: {}", PiiMasker.maskUsername(username));
            return;
        }
        AppUser user = AppUser.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .displayName(displayName)
                .enabled(true)
                .roles(new HashSet<>(roles))
                .tenantId(tenantId)
                .build();
        appUserRepository.save(user);
        log.info("已建立 demo 使用者: username={} roles={} tenantId={}",
                PiiMasker.maskUsername(username), roles, tenantId);
    }
}
