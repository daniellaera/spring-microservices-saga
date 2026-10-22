package com.daniellaera.sagae2e.steps;

import com.daniellaera.sagae2e.support.SagaEnvironment;
import io.cucumber.java.After;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.Scenario;
import org.awaitility.Awaitility;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public class Hooks {

    private static final int LOG_TAIL_LINES = 200;

    @BeforeAll
    public static void startEnvironment() {
        // Generous: the order-service outbox relay alone runs only every 5 seconds
        Awaitility.setDefaultTimeout(Duration.ofSeconds(90));
        Awaitility.setDefaultPollInterval(Duration.ofMillis(500));
        Awaitility.ignoreExceptionsByDefault();
        SagaEnvironment.get();
    }

    @After
    public void printServiceLogsOnFailure(Scenario scenario) {
        if (!scenario.isFailed()) {
            return;
        }
        SagaEnvironment environment = SagaEnvironment.get();
        for (String service : environment.serviceNames()) {
            String logs = environment.logsOf(service);
            scenario.attach(logs.getBytes(StandardCharsets.UTF_8), "text/plain", service + ".log");
            List<String> lines = logs.lines().toList();
            System.out.println("===== " + service + " (last " + LOG_TAIL_LINES + " lines) after failed scenario \""
                    + scenario.getName() + "\" =====");
            lines.subList(Math.max(0, lines.size() - LOG_TAIL_LINES), lines.size()).forEach(System.out::println);
        }
    }
}
