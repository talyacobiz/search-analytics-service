package com.talya.searchanalytics.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class TestCorsController {

    @GetMapping("/test-cors")
    public ResponseEntity<?> testCors() {
        return ResponseEntity.ok(Map.of("message", "CORS is working", "timestamp", System.currentTimeMillis()));
    }

    @PostMapping("/dashboard/test")
    public ResponseEntity<?> testDashboard(@RequestBody(required = false) Object body) {
        return ResponseEntity.ok(Map.of("message", "Dashboard endpoint working", "received", body));
    }

    @PostMapping("/dashboard/search-test")
    public ResponseEntity<?> testSearch(@RequestBody(required = false) Object body) {
        return ResponseEntity.ok(Map.of("message", "Search test working", "received", body, "timestamp", System.currentTimeMillis()));
    }

    @RequestMapping(value = "/dashboard/test", method = RequestMethod.OPTIONS)
    public void testOptions(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.setStatus(200);
    }
}
