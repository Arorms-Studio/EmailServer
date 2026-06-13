package cn.arorms.infra.email.security;

import cn.arorms.infra.email.entities.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public final class AppUserPrincipal implements UserDetails {

    private static final List<GrantedAuthority> AUTHORITIES =
            List.of(new SimpleGrantedAuthority("ROLE_USER"));

    private final Long userId;
    private final String username;
    private final String emailAddress;
    private final String passwordHash;
    private final boolean enabled;

    public AppUserPrincipal(Long userId, String username, String emailAddress,
                            String passwordHash, boolean enabled) {
        this.userId = userId;
        this.username = username;
        this.emailAddress = emailAddress;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
    }

    public static AppUserPrincipal from(User user) {
        return new AppUserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getEmailAddress(),
                user.getPassword(),
                user.isEnabled()
        );
    }

    public Long getUserId() { return userId; }
    public String getEmailAddress() { return emailAddress; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return AUTHORITIES; }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}