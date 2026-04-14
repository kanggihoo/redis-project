package com.example.wepay.controller;

import com.example.wepay.service.SessionDemoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/session")
public class SessionController {

    private final SessionDemoService sessionDemoService;

    public SessionController(SessionDemoService sessionDemoService) {
        this.sessionDemoService = sessionDemoService;
    }

    @PostMapping("/{key}")
    public Map<String, String> set(HttpSession session,
                                    @PathVariable String key,
                                    @RequestBody Map<String, String> body) {
        sessionDemoService.setAttribute(session, key, body.get("value"));
        return Map.of("sessionId", session.getId());
    }

    @GetMapping("/{key}")
    public Map<String, String> get(HttpSession session, @PathVariable String key) {
        String value = sessionDemoService.getAttribute(session, key);
        return Map.of("value", value != null ? value : "");
    }
}
