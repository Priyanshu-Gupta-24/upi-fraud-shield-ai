package com.priyanshu.upifraudshieldai.user.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter
{

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // Skip if no Bearer token present
        if (authHeader == null || !authHeader.startsWith("Bearer "))
        {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);

        if (!jwtUtil.isTokenValid(token))
        {
            log.warn("Invalid or expired JWT token from IP: {}", request.getRemoteAddr());
            filterChain.doFilter(request, response);
            return;
        }

        final String username = jwtUtil.extractUsername(token);

        // Only set auth if not already authenticated
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null)
        {
            try
            {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities());

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.debug("Authenticated user: {}", username);

            }
            catch (Exception e)
            {
                log.warn("Failed to authenticate user {}: {}", username, e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    // Skip filter for public paths
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request)
    {
        String path = request.getServletPath();
        return path.startsWith("/api/auth/")
                || path.startsWith("/actuator/");
    }
}