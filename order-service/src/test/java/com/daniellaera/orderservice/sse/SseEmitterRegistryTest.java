package com.daniellaera.orderservice.sse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SseEmitterRegistryTest {

    // Subclass that returns mock emitters whose send() throws (simulates dead connections)
    private SseEmitterRegistry registryWithDeadEmitters;
    private SseEmitterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SseEmitterRegistry();

        registryWithDeadEmitters = new SseEmitterRegistry() {
            @Override
            protected SseEmitter createEmitter() {
                SseEmitter mock = mock(SseEmitter.class);
                try {
                    doThrow(new IOException("dead connection"))
                            .when(mock).send(any(SseEmitter.SseEventBuilder.class));
                } catch (IOException ignored) {}
                return mock;
            }
        };
    }

    @Test
    void register_addsEmitterForUser() {
        SseEmitter emitter = registry.register("user@test.com");

        assertThat(emitter).isNotNull();
        assertThat(registry.totalConnections()).isEqualTo(1);
    }

    @Test
    void register_supportsMultipleEmittersPerUser() {
        registry.register("user@test.com");
        registry.register("user@test.com");

        assertThat(registry.totalConnections()).isEqualTo(2);
    }

    @Test
    void totalConnections_returnsCorrectCountAcrossUsers() {
        registry.register("user1@test.com");
        registry.register("user1@test.com");
        registry.register("user2@test.com");

        assertThat(registry.totalConnections()).isEqualTo(3);
    }

    @Test
    void pushToUser_sendsToCorrectUserOnly() {
        registryWithDeadEmitters.register("user1@test.com");
        registryWithDeadEmitters.register("user2@test.com");
        assertThat(registryWithDeadEmitters.totalConnections()).isEqualTo(2);

        // Push to user1 — send() throws, so user1's emitter is removed.
        // user2's emitter is untouched.
        registryWithDeadEmitters.pushToUser("user1@test.com", "payload");

        assertThat(registryWithDeadEmitters.totalConnections()).isEqualTo(1);
    }

    @Test
    void deadEmitterRemovedAutomaticallyOnError() {
        registryWithDeadEmitters.register("user@test.com");
        assertThat(registryWithDeadEmitters.totalConnections()).isEqualTo(1);

        registryWithDeadEmitters.pushToUser("user@test.com", "payload");

        assertThat(registryWithDeadEmitters.totalConnections()).isEqualTo(0);
    }

    @Test
    void pushToAll_sendsToAllConnectedUsers() {
        registryWithDeadEmitters.register("user1@test.com");
        registryWithDeadEmitters.register("user2@test.com");
        assertThat(registryWithDeadEmitters.totalConnections()).isEqualTo(2);

        // All emitters fail on send — all get cleaned up.
        registryWithDeadEmitters.pushToAll("payload");

        assertThat(registryWithDeadEmitters.totalConnections()).isEqualTo(0);
    }
}
