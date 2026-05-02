package com.mindmap.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.mindmap.security.JwtTokenProvider;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");

        Map<String, String> response = new HashMap<>();
        response.put("message", "User registered successfully");
        response.put("email", email);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");

        String token = jwtTokenProvider.generateToken(email, "user-123");

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("user", Map.of("email", email, "id", "user-123"));
        response.put("expiresIn", 86400);

        return ResponseEntity.ok(response);
    }
}
