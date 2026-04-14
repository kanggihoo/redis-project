package com.example.wepay.interceptor;

import com.example.wepay.service.SlidingWindowRateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final Map<String, SlidingWindowRateLimitService> serviceMap = new ConcurrentHashMap<>();

    public RateLimitInterceptor(StringRedisTemplate stringRedisTemplate,
                                 @Value("${rate-limit.transfer.max-requests}") int transferMax,
                                 @Value("${rate-limit.transfer.window-seconds}") long transferWindow,
                                 @Value("${rate-limit.account-read.max-requests}") int accountMax,
                                 @Value("${rate-limit.account-read.window-seconds}") long accountWindow) {
        serviceMap.put("transfer", new SlidingWindowRateLimitService(stringRedisTemplate, transferMax, transferWindow));
        serviceMap.put("accounts", new SlidingWindowRateLimitService(stringRedisTemplate, accountMax, accountWindow));
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                              HttpServletResponse response,
                              Object handler) throws Exception {
        String identifier = request.getRemoteAddr();
        String apiKey = extractApiKey(request.getRequestURI());

        SlidingWindowRateLimitService service = serviceMap.get(apiKey);
        if (service != null && !service.isAllowed(apiKey, identifier)) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded\"}");
            return false;
        }
        return true;
    }

    private String extractApiKey(String uri) {
        if (uri.startsWith("/api/transfer")) {
            return "transfer";
        }
        if (uri.startsWith("/api/accounts")) {
            return "accounts";
        }
        return "default";
    }
}
