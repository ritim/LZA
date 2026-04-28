package com.lza.aethercare.common.security;

import com.lza.aethercare.userprofile.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UserDetailsService：根據 username 查 AppUser 並包裝成 AppUserDetails。 */
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return appUserRepository.findByUsername(username)
                .map(AppUserDetails::from)
                .orElseThrow(() -> new UsernameNotFoundException("使用者不存在: " + username));
    }
}
