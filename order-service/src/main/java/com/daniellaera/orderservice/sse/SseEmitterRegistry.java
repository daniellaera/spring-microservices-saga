package com.daniellaera.orderservice.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class SseEmitterRegistry {

    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    protected SseEmitter createEmitter() {
        return new SseEmitter(Long.MAX_VALUE);
    }

    public SseEmitter register(String userEmail) {
        List<SseEmitter> existing = emitters.computeIfAbsent(
                userEmail, k -> Collections.synchronizedList(new ArrayList<>()));

        while (existing.size() >= 2) {
            SseEmitter old = existing.remove(0);
            try { old.complete(); } catch (Exception ignored) {}
        }

        SseEmitter emitter = createEmitter();
        existing.add(emitter);

        Runnable cleanup = () -> removeEmitter(userEmail, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        log.info("=== SSE: registered emitter for {}, total: {}",
                userEmail, existing.size());
        return emitter;
    }

    public void pushToUser(String userEmail, Object data) {
        List<SseEmitter> userEmitters = emitters.getOrDefault(userEmail, List.of());

        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("order-update")
                        .data(data, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                log.warn("=== SSE: failed to push to {}: {}", userEmail, e.getMessage());
                dead.add(emitter);
            }
        }
        dead.forEach(e -> removeEmitter(userEmail, e));
    }

    public void pushToAll(Object data) {
        emitters.keySet().forEach(email -> pushToUser(email, data));
    }

    private void removeEmitter(String userEmail, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(userEmail);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) emitters.remove(userEmail);
        }
    }

    public int totalConnections() {
        return emitters.values().stream().mapToInt(List::size).sum();
    }
}
