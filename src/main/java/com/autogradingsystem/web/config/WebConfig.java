package com.autogradingsystem.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebConfig - Spring MVC Configuration for Frontend Resources
 * 
 * PURPOSE:
 * - Maps /css/** URLs to frontend/static/css/ folder
 * - Configures Thymeleaf to look for templates in frontend/templates/
 * 
 * WHY NEEDED:
 * - We renamed resources/ → frontend/
 * - Spring Boot expects resources/static/ and resources/templates/ by default
 * - This config tells Spring to look in frontend/ instead
 * 
 * MAPPINGS:
 * - http://localhost:9090/css/style.css → frontend/static/css/style.css
 * - index.html view → frontend/templates/index.html
 * 
 * @author IS442 Team
 * @version 2.0
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Configure static resource handlers
     * Maps URL paths to physical folder locations
     */
    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        
        // Map /css/**
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/");
        
        // Map /js/** (if you add JavaScript files later)
        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/");
        
        // Map /images/**  (if you add images later)
        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/images/");
    }
}
