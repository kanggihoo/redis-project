package com.example.wepay.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

@Service
public class SessionDemoService {

    public void setAttribute(HttpSession session, String key, String value) {
        session.setAttribute(key, value);
    }

    public String getAttribute(HttpSession session, String key) {
        Object value = session.getAttribute(key);
        return value != null ? value.toString() : null;
    }
}
