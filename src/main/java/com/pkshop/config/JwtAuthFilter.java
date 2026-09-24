package com.pkshop.config;

import com.pkshop.domain.user.entity.User;
import com.pkshop.domain.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepo;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepo) {
        this.jwtService = jwtService;
        this.userRepo = userRepo;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "GET".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().startsWith("/api/customer/shop/products");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // 1. ถ้าเป็นการเรียก API หมวด Auth (Login/Register) ให้ข้ามการตรวจ Token ทันที
        String path = request.getRequestURI();
        if (path.startsWith("/api/auth")) {
            chain.doFilter(request, response);
            return;
        }

        // 2. ตรวจสอบ Header
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = auth.substring(7);
        try {
            Claims claims = jwtService.parse(token).getPayload();
            Long userId = Long.valueOf(claims.getSubject());

            User user = userRepo.findById(userId).orElse(null);
            if (user != null) {
                var authorities = user.getRoles().stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r.getName()))
                        .toList();
                var authentication = new UsernamePasswordAuthenticationToken(
                        user, null, authorities
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        catch (ExpiredJwtException e) {
            // 🚀 ถ้า Token หมดอายุ ให้ตอบกลับเป็น 401 ทันที และหยุดการทำงาน
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\": \"JWT Token expired. Please login again.\"}");
            return;
        }
        catch (JwtException e) {
            // ถ้า Token ผิดรูปแบบ หรือปลอมแปลง
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\": \"Invalid JWT Token.\"}");
            return;
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        chain.doFilter(request, response);
    }
}