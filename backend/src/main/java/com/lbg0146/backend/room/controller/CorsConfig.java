package com.lbg0146.backend.room.controller;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// 프론트엔드 개발 서버(Vite, localhost:5173)에서 REST API를 호출할 수 있도록 개발 환경에서만
// 필요한 최소 CORS 허용. WebSocket(/ws)은 WebSocketConfig에서 이미 별도로 전체 허용 중이라
// 여기서는 REST(/api/**)만 다룬다.
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST");
    }
}
