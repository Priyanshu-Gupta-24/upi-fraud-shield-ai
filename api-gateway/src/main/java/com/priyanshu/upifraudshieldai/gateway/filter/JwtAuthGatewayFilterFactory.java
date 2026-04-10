package com.priyanshu.upifraudshieldai.gateway.filter;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * JwtAuthGatewayFilterFactory

 * Named "JwtAuth" in application.yml — Spring Cloud Gateway strips
 * "GatewayFilterFactory" from the class name automatically.

 * This filter is only applied to routes that explicitly list "- JwtAuth"
 * in their filters section. Public routes (health, auth) do NOT list it,
 * so this filter never runs on those routes at all.

 * On valid JWT: extracts userId, role, username and forwards them
 * as X-User-Id, X-User-Role, X-Username headers to the downstream service.

 * On missing/invalid JWT: returns 401 JSON immediately without
 * forwarding the request to the downstream service.
 */
@Component
@Slf4j
public class JwtAuthGatewayFilterFactory
        extends AbstractGatewayFilterFactory<JwtAuthGatewayFilterFactory.Config>
{

    @Value("${jwt.secret}")
    private String secret;

    public JwtAuthGatewayFilterFactory()
    {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config)
    {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer "))
            {
                log.warn("Missing Authorization header for protected path: {}", path);
                return unauthorized(exchange, "Missing or invalid Authorization header");
            }

            String token = authHeader.substring(7);

            try
            {
                Claims claims = Jwts.parser()
                        .verifyWith(signingKey())
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                String userId   = claims.getSubject();
                String role     = claims.get("role",     String.class);
                String username = claims.get("username", String.class);

                log.debug("JWT validated at gateway: user={} role={} path={}",
                        username, role, path);

                ServerHttpRequest mutatedRequest = request.mutate()
                        .header("X-User-Id",   userId   != null ? userId   : "")
                        .header("X-User-Role", role     != null ? role     : "USER")
                        .header("X-Username",  username != null ? username : "")
                        .build();

                return chain.filter(exchange.mutate().request(mutatedRequest).build());

            }
            catch (ExpiredJwtException e)
            {
                log.warn("Expired JWT for path {}: {}", path, e.getMessage());
                return unauthorized(exchange, "Token has expired. Please login again.");
            }
            catch (JwtException e)
            {
                log.warn("Invalid JWT for path {}: {}", path, e.getMessage());
                return unauthorized(exchange, "Invalid token.");
            }
        };
    }

    private SecretKey signingKey()
    {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message)
    {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
                "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"%s\"}",
                message);
        var buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    public static class Config
    {
        // No config needed — reads jwt.secret from application.yml
    }
}