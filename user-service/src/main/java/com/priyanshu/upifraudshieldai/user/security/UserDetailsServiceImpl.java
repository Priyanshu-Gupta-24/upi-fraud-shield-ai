package com.priyanshu.upifraudshieldai.user.security;

import com.priyanshu.upifraudshieldai.user.entity.User;
import com.priyanshu.upifraudshieldai.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService
{

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {

        User user = userRepository
                .findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No user found with username or email: " + usernameOrEmail));

        // Account lock check
        if (user.isAccountLocked())
        {
            throw new UsernameNotFoundException(
                    "Account is locked until " + user.getLockedUntil());
        }

        if (user.getStatus() == User.UserStatus.SUSPENDED)
        {
            throw new UsernameNotFoundException("Account is suspended");
        }

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(List.of(
                        new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .accountExpired(false)
                .accountLocked(user.isAccountLocked())
                .credentialsExpired(false)
                .disabled(user.getStatus() == User.UserStatus.SUSPENDED)
                .build();
    }
}