package com.priyanshu.upifraudshieldai.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
public class SecurityConfig
{

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http)
    {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/api/auth/**").permitAll()
                        .pathMatchers("/actuator/**").permitAll()
                        .anyExchange().permitAll()
                )
                .build();
    }
}