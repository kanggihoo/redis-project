package com.example.wepay.controller;

import com.example.wepay.service.JwtTokenService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtTokenService jwtTokenService;

    public AuthController(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @PostMapping("/login")
    public Map<String, String> login(@RequestBody Map<String, String> request) {
        String userId = request.get("userId");
        String token = jwtTokenService.generateToken(userId);
        return Map.of("token", token);
    }

    @PostMapping("/logout")
    public Map<String, String> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring("Bearer ".length());
        jwtTokenService.blacklist(token);
        return Map.of("status", "LOGGED_OUT");
    }
}
