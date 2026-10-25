package com.daniellaera.configserver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.cloud.config.server.environment.EnvironmentController;
import org.springframework.cloud.config.server.environment.NativeEnvironmentRepository;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Answers 404 for an application without a config file of its own in the search locations. The native backend
 * would otherwise answer with the shared application.yml alone, and a service whose configs/&lt;name&gt;.yml is missing
 * or renamed would start on its local defaults (localhost database, no secrets) and fail later, unclearly. On the
 * 404 a client fails its startup (fail-fast: "None of labels [] found"). Only the environment endpoints are
 * checked; the health indicator reads the repository directly and is not affected.
 */
class ApplicationFileRequired implements HandlerInterceptor {

    private static final List<String> EXTENSIONS = List.of(".yml", ".yaml", ".properties");

    private final NativeEnvironmentRepository repository;
    private final ResourceLoader resourceLoader;

    ApplicationFileRequired(NativeEnvironmentRepository repository, ResourceLoader resourceLoader) {
        this.repository = repository;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!(handler instanceof HandlerMethod method) || method.getBeanType() != EnvironmentController.class) {
            return true;
        }
        @SuppressWarnings("unchecked")
        Map<String, String> variables =
                (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String names = variables == null ? null : variables.get("name");
        if (names == null) {
            return true;
        }
        String[] profiles = variables.getOrDefault("profiles", "default").split(",");
        for (String name : names.split(",")) {
            if (!hasOwnFile(name, profiles)) {
                response.sendError(HttpStatus.NOT_FOUND.value(), "No configuration file for application " + name);
                return false;
            }
        }
        return true;
    }

    // <name>.yml, .yaml or .properties, or a profile-specific <name>-<profile>.* in one of the search locations
    private boolean hasOwnFile(String name, String[] profiles) {
        for (String location : repository.getSearchLocations()) {
            String directory = location.endsWith("/") ? location : location + "/";
            for (String extension : EXTENSIONS) {
                if (exists(directory + name + extension) || Arrays.stream(profiles)
                        .anyMatch(profile -> exists(directory + name + "-" + profile + extension))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean exists(String location) {
        return resourceLoader.getResource(location).exists();
    }
}
