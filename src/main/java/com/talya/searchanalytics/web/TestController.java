/*
package com.talya.searchanalytics.web;

import javax.servlet.http.HttpServletResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestMethod;

@RestController
@CrossOrigin(origins = "https://searchwithai.myshopify.com")
public class TestController {

    @PostMapping("/search")
    public ResponseEntity<?> testSearch(@RequestBody(required = false) String body) {
        return ResponseEntity.ok("{\"status\": \"CORS working\", \"received\": \"" + body + "\"}");
    }

    @GetMapping("/search")
    public ResponseEntity<?> testSearchGet() {
        return ResponseEntity.ok("{\"status\": \"CORS working - GET\"}");
    }

    @RequestMapping(value = "/search", method = RequestMethod.OPTIONS)
    public void corsHeaders(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "https://searchwithai.myshopify.com");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
*/
