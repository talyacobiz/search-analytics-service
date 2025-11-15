package com.talya.searchanalytics.web.dto;

import lombok.*;
import java.time.Instant;

public class TermsDTOs {

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AgreeRequest {
        private String shopId;
        private String termsVersion; // optional
        private Instant acceptedAt;  // optional; if null server sets now
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AgreeResponse {
        private boolean ok;
        private String shopId;
        private String termsVersion;
        private Instant acceptedAt;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StatusResponse {
        private boolean agreed;
        private String shopId;
        private String termsVersion;
        private Instant acceptedAt;
    }
}
