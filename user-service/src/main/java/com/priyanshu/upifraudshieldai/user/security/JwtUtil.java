package com.priyanshu.upifraudshieldai.user.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
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

    @Getter
    @Value("${jwt.expiration}")
    private long expiration;

    private SecretKey signingKey() 
    {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // Generate token
    public String generateToken(String username, String role) 
    {
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(signingKey())
                .compact();
    }

    // Parse all claims 
    public Claims extractAllClaims(String token)
    {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Extract username (subject) 
    public String extractUsername(String token)
    {
        return extractAllClaims(token).getSubject();
    }

    // Extract role claim
    public String extractRole(String token)
    {
        return extractAllClaims(token).get("role", String.class);
    }

    // Validate token
    public boolean isTokenValid(String token) 
    {
        try
        {
            Claims claims = extractAllClaims(token);
            return !claims.getExpiration().before(new Date());
        }
        catch (ExpiredJwtException e)
        {
            log.warn("JWT token is expired: {}", e.getMessage());
        }
        catch (MalformedJwtException e)
        {
            log.warn("JWT token is malformed: {}", e.getMessage());
        }
        catch (JwtException | IllegalArgumentException e)
        {
            log.warn("JWT token is invalid: {}", e.getMessage());
        }
        return false;
    }

}