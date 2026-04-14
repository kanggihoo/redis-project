package com.example.wepay.interceptor;

import com.example.wepay.service.JwtTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtBlacklistInterceptor implements HandlerInterceptor {

    private final JwtTokenService jwtTokenService;

    public JwtBlacklistInterceptor(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                              HttpServletResponse response,
                              Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return true; // Authorization 헤더 없으면 통과 (선택적 인증)
        }

        String token = authHeader.substring("Bearer ".length());
        if (jwtTokenService.isBlacklisted(token)) {
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Token has been blacklisted\"}");
            return false;
        }

        return true;
    }
}
