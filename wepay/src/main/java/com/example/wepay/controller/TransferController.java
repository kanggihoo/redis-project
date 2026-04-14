package com.example.wepay.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/transfer")
public class TransferController {

    @PostMapping
    public Map<String, Object> transfer(@RequestBody Map<String, Object> request) {
        return Map.of("status", "SUCCESS",
                       "message", "송금 완료 (Mock)",
                       "amount", request.getOrDefault("amount", 0));
    }
}
