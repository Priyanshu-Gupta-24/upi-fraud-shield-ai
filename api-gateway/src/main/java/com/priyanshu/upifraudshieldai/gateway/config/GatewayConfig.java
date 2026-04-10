package com.priyanshu.upifraudshieldai.gateway.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * GatewayConfig — beans required by the gateway.

 * Two KeyResolver beans:

 * ipKeyResolver — used for PUBLIC endpoints (login, register).
 *   Rate limits by IP address. Prevents brute force and credential stuffing.
 *   Different IPs get separate buckets.

 * userKeyResolver — used for PROTECTED endpoints.
 *   Rate limits by userId extracted from the JWT.
 *   Prevents a single authenticated user from hammering the API.
 *   Falls back to IP if JWT is missing/invalid (shouldn't happen since
 *   JwtAuthFilter runs first, but defensive programming).
 */
@Configuration
@Slf4j
public class GatewayConfig
{

    @Value("${jwt.secret}")
    private String secret;

    /**
     * Rate limit key for public routes — by IP address.
     * Stops bots from brute-forcing login with many accounts from one IP.
     */
    @Bean
    public KeyResolver ipKeyResolver()
    {
        return exchange ->
        {
            String ip = exchange.getRequest()
                    .getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just("ip:" + ip);
        };
    }

    /**
     * Rate limit key for protected routes — by userId from JWT.
     * Each authenticated user gets their own rate limit bucket.
     * Falls back to IP if token not present.
     */
    @Bean
    @Primary
    public KeyResolver userKeyResolver()
    {
        return exchange ->
        {
            String authHeader = exchange.getRequest()
                    .getHeaders().getFirst("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer "))
            {
                try
                {
                    String token  = authHeader.substring(7);
                    SecretKey key = Keys.hmacShaKeyFor(
                            secret.getBytes(StandardCharsets.UTF_8));
                    Claims claims = Jwts.parser()
                            .verifyWith(key)
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();
                    String userId = claims.getSubject();
                    if (userId != null)
                        return Mono.just("user:" + userId);
                }
                catch (Exception e)
                {
                    log.debug("Could not extract userId for rate limit key: {}", e.getMessage());
                }
            }

            // Fallback: rate limit by IP
            String ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just("ip:" + ip);
        };
    }
}