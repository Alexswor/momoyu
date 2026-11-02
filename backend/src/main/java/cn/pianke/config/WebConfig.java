package cn.pianke.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.util.Arrays;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String[] origins;
    private final String desktopOrigin;

    public WebConfig(@Value("${app.cors-origins:}") String origins,
            @Value("${app.desktop.origin:http://127.0.0.1:5173}") String desktopOrigin) {
        this.desktopOrigin = desktopOrigin;
        this.origins = Arrays.stream(origins.split(",")).map(String::trim)
                .filter(origin -> !origin.isEmpty()).toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/desktop/**").allowedOrigins(desktopOrigin)
                .allowedMethods("GET", "POST").maxAge(600);
        registry.addMapping("/api/**").allowedOrigins(origins).allowedMethods("GET").maxAge(3600);
    }
}
