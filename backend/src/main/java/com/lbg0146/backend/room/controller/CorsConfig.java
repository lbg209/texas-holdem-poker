package com.lbg0146.backend.room.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// 프론트엔드에서 REST API를 호출할 수 있도록 허용하는 CORS 설정. 허용 origin은
// app.cors.allowed-origins 프로퍼티(콤마로 여러 개 가능)로 주입받아, 로컬 개발(Vite,
// localhost:5173)과 배포 환경(프론트 배포 주소)에서 같은 코드로 값만 다르게 쓴다.
// WebSocket(/ws)은 WebSocketConfig에서 이미 별도로 전체 허용 중이라 여기서는 REST(/api/**)만 다룬다.
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST");
    }
}
