package com.daniellaera.configserver;

import org.springframework.cloud.config.server.environment.NativeEnvironmentRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class ConfigServerWebConfig implements WebMvcConfigurer {

    private final NativeEnvironmentRepository repository;
    private final ResourceLoader resourceLoader;

    ConfigServerWebConfig(NativeEnvironmentRepository repository, ResourceLoader resourceLoader) {
        this.repository = repository;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ApplicationFileRequired(repository, resourceLoader));
    }
}
