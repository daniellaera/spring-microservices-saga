package com.daniellaera.configserver;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.config.client.ConfigClientFailFastException;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import java.util.Properties;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The configserver import of every client of this config server, with the client's own application.yml (read from
 * its module) against a stub config server: a config server that comes up late is waited for, one that never does,
 * or that has no config for the client, fails the startup. Only the configuration phase runs: the application has
 * no beans. The backoff is shortened here; the production budget is pinned by productionBudget.
 */
class ClientConfigImportTest {

    // max-attempts in every client's application.yml
    private static final int ATTEMPTS = 10;

    @RegisterExtension
    static WireMockExtension configServer = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Configuration(proxyBeanMethods = false)
    static class NoBeans {
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"audit-trail-service", "auth-service", "cart-service", "gateway-service",
            "inventory-service", "order-service", "payment-service"})
    void aConfigServerThatComesUpLate_isWaitedFor(String service) {
        // no answer twice (connection reset), a 503 once, then the config
        configServer.stubFor(WireMock.get("/" + service + "/default").inScenario("late").whenScenarioStateIs(STARTED)
                .willReturn(WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)).willSetStateTo("reset once"));
        configServer.stubFor(WireMock.get("/" + service + "/default").inScenario("late").whenScenarioStateIs("reset once")
                .willReturn(WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)).willSetStateTo("starting"));
        configServer.stubFor(WireMock.get("/" + service + "/default").inScenario("late").whenScenarioStateIs("starting")
                .willReturn(WireMock.serviceUnavailable()).willSetStateTo("up"));
        configServer.stubFor(WireMock.get("/" + service + "/default").inScenario("late").whenScenarioStateIs("up")
                .willReturn(WireMock.okJson(environment(service, """
                        {"name":"classpath:/configs/%s.yml","source":{"probe.loaded":"yes"}}""".formatted(service)))));

        try (ConfigurableApplicationContext context = start(service)) {
            assertThat(context.getEnvironment().getProperty("probe.loaded")).isEqualTo("yes");
        }
        configServer.verify(4, WireMock.getRequestedFor(WireMock.urlEqualTo("/" + service + "/default")));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"audit-trail-service", "auth-service", "cart-service", "gateway-service",
            "inventory-service", "order-service", "payment-service"})
    void aConfigServerThatNeverComesUp_failsTheStartup_afterTheLastAttempt(String service) {
        configServer.stubFor(WireMock.get("/" + service + "/default").willReturn(WireMock.serviceUnavailable()));

        assertThatThrownBy(() -> start(service).close())
                .isInstanceOf(ConfigClientFailFastException.class)
                .hasMessageContaining("Could not locate PropertySource");
        configServer.verify(ATTEMPTS, WireMock.getRequestedFor(WireMock.urlEqualTo("/" + service + "/default")));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"audit-trail-service", "auth-service", "cart-service", "gateway-service",
            "inventory-service", "order-service", "payment-service"})
    void aConfigServerWithoutTheClientsConfig_failsTheStartup(String service) {
        // what the config server answers for an unknown label: not an empty config to start on
        configServer.stubFor(WireMock.get("/" + service + "/default").willReturn(WireMock.notFound()));

        assertThatThrownBy(() -> start(service).close())
                .isInstanceOf(ConfigClientFailFastException.class)
                .hasMessageContaining("None of labels");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"audit-trail-service", "auth-service", "cart-service", "gateway-service",
            "inventory-service", "order-service", "payment-service"})
    void productionBudget(String service) {
        Properties yaml = applicationYml(service);

        // mandatory: never "optional:configserver:"
        assertThat(yaml.getProperty("spring.config.import")).startsWith("configserver:");
        assertThat(yaml.getProperty("spring.cloud.config.fail-fast")).isEqualTo("true");
        // waits 2, 3, 4.5, 6.75, 10.1 s, then 15 s: about 85 s over 10 attempts
        assertThat(yaml.getProperty("spring.cloud.config.retry.initial-interval")).isEqualTo("2000");
        assertThat(yaml.getProperty("spring.cloud.config.retry.multiplier")).isEqualTo("1.5");
        assertThat(yaml.getProperty("spring.cloud.config.retry.max-interval")).isEqualTo("15000");
        assertThat(yaml.getProperty("spring.cloud.config.retry.max-attempts")).isEqualTo(String.valueOf(ATTEMPTS));
        // a config server that accepts the connection but never answers must not hold an attempt for 185 s
        assertThat(yaml.getProperty("spring.cloud.config.request-connect-timeout")).isEqualTo("3000");
        assertThat(yaml.getProperty("spring.cloud.config.request-read-timeout")).isEqualTo("10000");
    }

    // The client's own application.yml, pointed at the stub, with the backoff shortened (command-line arguments
    // outrank the file); max-attempts and the multiplier stay the file's. spring.cloud.config.enabled: this JVM has
    // config-server on its classpath, whose ConfigServerBootstrapApplicationListener switches the client off.
    private static ConfigurableApplicationContext start(String service) {
        return new SpringApplicationBuilder(NoBeans.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .run("--spring.cloud.config.enabled=true",
                        "--spring.config.location=file:../" + service + "/src/main/resources/application.yml",
                        "--CONFIG_SERVER_URI=" + configServer.baseUrl(),
                        "--spring.cloud.config.retry.initial-interval=20",
                        "--spring.cloud.config.retry.max-interval=50");
    }

    private static Properties applicationYml(String service) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("../" + service + "/src/main/resources/application.yml"));
        return yaml.getObject();
    }

    private static String environment(String service, String propertySource) {
        return """
                {"name":"%s","profiles":["default"],"label":null,"version":null,"state":null,"propertySources":[%s]}"""
                .formatted(service, propertySource);
    }
}
