package com.sapphire.hawe.global.config;

import com.sapphire.hawe.global.storage.StorageProperties;
import java.nio.file.Path;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private final StorageProperties properties;
    public WebMvcConfig(StorageProperties properties) { this.properties = properties; }
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(properties.localDir()).toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) location += "/";
        registry.addResourceHandler("/files/**").addResourceLocations(location);
    }
}
