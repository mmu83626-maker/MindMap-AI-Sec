package com.mindmap.feishu;

import com.mindmap.academic.service.FeishuService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/feishu")
@CrossOrigin(origins = "*")
public class FeishuController {

    private final FeishuService feishuService;

    public FeishuController(FeishuService feishuService) {
        this.feishuService = feishuService;
    }

    @PostMapping("/events")
    public Map<String, Object> events(@RequestBody Map<String, Object> payload) {
        return feishuService.handleEvent(payload);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return feishuService.health();
    }
}
