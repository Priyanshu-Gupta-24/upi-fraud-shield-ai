package com.priyanshu.upifraudshieldai.transaction.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@Slf4j
public class JwtUtil
{

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey signingKey()
    {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Claims extractAllClaims(String token)
    {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token)
    {
        return extractAllClaims(token).getSubject();
    }

    public String extractRole(String token)
    {
        return extractAllClaims(token).get("role", String.class);
    }

    public boolean isTokenValid(String token)
    {
        try
        {
            Claims claims = extractAllClaims(token);
            return !claims.getExpiration().before(new Date());
        }
        catch (ExpiredJwtException e)
        {
            log.warn("JWT expired: {}", e.getMessage());
        }
        catch (JwtException | IllegalArgumentException e)
        {
            log.warn("JWT invalid: {}", e.getMessage());
        }
        return false;
    }
}