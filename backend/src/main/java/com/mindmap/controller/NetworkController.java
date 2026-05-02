package com.mindmap.controller;

import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/network")
@CrossOrigin(origins = "*")
public class NetworkController {

    @PostMapping("/speedtest")
    public Map<String, Object> runSpeedTest() {
        Map<String, Object> result = new HashMap<>();
        result.put("latency", Math.random() * 100);
        result.put("downloadSpeed", Math.random() * 100);
        result.put("uploadSpeed", Math.random() * 50);
        result.put("jitter", Math.random() * 20);
        result.put("timestamp", LocalDateTime.now());
        return result;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "healthy");
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }
}
