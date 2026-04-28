package com.lza.aethercare.common.security;

import com.lza.aethercare.userprofile.entity.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/** Spring Security UserDetails wrapper：暴露 user id + roles 給後續 controller / service 使用。 */
public class AppUserDetails implements UserDetails {

    private final Long id;
    private final String username;
    private final String passwordHash;
    private final boolean enabled;
    private final Set<String> roles;
    private final Collection<? extends GrantedAuthority> authorities;

    public AppUserDetails(Long id, String username, String passwordHash, boolean enabled, Set<String> roles) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.roles = roles == null ? Set.of() : Set.copyOf(roles);
        this.authorities = this.roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 從 DB entity 轉成 UserDetails。 */
    public static AppUserDetails from(AppUser user) {
        return new AppUserDetails(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.isEnabled(),
                user.getRoles());
    }

    /** 從 JWT token claims 重建（無需查 DB）。 */
    public static AppUserDetails fromToken(Long id, String username, Set<String> roles) {
        return new AppUserDetails(id, username, "", true, roles);
    }

    public Long getId() {
        return id;
    }

    public Set<String> getRoles() {
        return roles;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities == null ? Collections.emptyList() : authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
