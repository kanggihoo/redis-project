package com.example.wepay.config;

import com.example.wepay.interceptor.JwtBlacklistInterceptor;
import com.example.wepay.interceptor.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final JwtBlacklistInterceptor jwtBlacklistInterceptor;

    public WebMvcConfig(RateLimitInterceptor rateLimitInterceptor,
                         JwtBlacklistInterceptor jwtBlacklistInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.jwtBlacklistInterceptor = jwtBlacklistInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Rate Limiting 먼저 (불필요한 JWT 파싱 방지)
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/transfer/**", "/api/accounts/**");

        // JWT Blacklist 체크 (로그인 제외)
        registry.addInterceptor(jwtBlacklistInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login");
    }
}
