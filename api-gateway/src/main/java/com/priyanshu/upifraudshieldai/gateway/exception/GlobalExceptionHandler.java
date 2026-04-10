package com.priyanshu.upifraudshieldai.gateway.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * GlobalExceptionHandler — unified JSON error responses for the gateway.

 * Order(-1) ensures this runs before the default Spring error handler.

 * Handles:
 * - 404 No route found — service path doesn't match any route
 * - 503 Service unavailable — downstream service is down / not in Eureka
 * - 429 Too many requests — rate limit exceeded
 * - 401 Unauthorized — from JwtAuthGatewayFilterFactory
 * - Everything else — 500 generic error

 * Why in the gateway?
 * Without this, Spring WebFlux returns HTML error pages by default,
 * which breaks the Angular frontend that expects JSON everywhere.
 */
@Component
@Order(-1)
@Slf4j
public class GlobalExceptionHandler implements ErrorWebExceptionHandler
{

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex)
    {
        var response = exchange.getResponse();
        String path  = exchange.getRequest().getURI().getPath();

        HttpStatus status;
        String message;

        if (ex instanceof ResponseStatusException rse)
        {
            status  = HttpStatus.valueOf(rse.getStatusCode().value());
            message = switch (status)
            {
                case NOT_FOUND              -> "Route not found: " + path;
                case SERVICE_UNAVAILABLE    -> "Service temporarily unavailable. Please try again.";
                case TOO_MANY_REQUESTS      -> "Rate limit exceeded. Please slow down.";
                case UNAUTHORIZED           -> "Authentication required.";
                case FORBIDDEN              -> "Access denied.";
                default                     -> rse.getReason() != null ? rse.getReason() : "An error occurred";
            };
        }
        else
        {
            status  = HttpStatus.SERVICE_UNAVAILABLE;
            message = "Service temporarily unavailable. Please try again shortly.";
            log.error("Gateway error for path [{}]: {}", path, ex.getMessage());
        }

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"path\":\"%s\",\"timestamp\":\"%s\"}",
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                LocalDateTime.now()
        );

        var buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}