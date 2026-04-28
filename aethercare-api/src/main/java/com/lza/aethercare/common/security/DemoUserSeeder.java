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
        upsert("admin", "admin123", "管理者", Set.of("USER", "ADMIN"));
        upsert("family01", "family123", "家屬一號（level 1）", Set.of("USER"));
        upsert("family02", "family123", "家屬二號（level 2）", Set.of("USER"));
        upsert("insurer01", "insurer123", "保險業者代表", Set.of("INSURANCE"));
    }

    private void upsert(String username, String rawPassword, String displayName, Set<String> roles) {
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
                .build();
        appUserRepository.save(user);
        log.info("已建立 demo 使用者: username={} roles={}", PiiMasker.maskUsername(username), roles);
    }
}
