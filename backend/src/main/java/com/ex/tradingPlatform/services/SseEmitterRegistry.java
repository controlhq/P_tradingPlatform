package com.ex.tradingPlatform.services;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Open SSE connections per user (one user may have several tabs). A connection is dropped on
 * completion, timeout, error or a failed send, so dead clients do not pile up.
 */
@Slf4j
@Component
public class SseEmitterRegistry {

    // EventSource reconnects by itself, so a finite timeout only recycles idle connections
    static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(Long userId) {
        SseEmitter emitter = newEmitter();
        emitters.computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> {
            remove(userId, emitter);
            emitter.complete();
        });
        emitter.onError(ex -> remove(userId, emitter));
        return emitter;
    }

    /** Sends a named event to every connection of one user. */
    public void sendToUser(Long userId, String name, Object data) {
        emitters.getOrDefault(userId, Set.of())
                .forEach(emitter -> send(userId, emitter, () -> SseEmitter.event().name(name).data(data)));
    }

    /** Sends a named event to every open connection. */
    public void broadcast(String name, Object data) {
        emitters.forEach((userId, set) ->
                set.forEach(emitter -> send(userId, emitter, () -> SseEmitter.event().name(name).data(data))));
    }

    /** A comment line keeps proxies from closing idle connections and reveals dead clients. */
    @Scheduled(fixedRate = 15_000)
    public void heartbeat() {
        emitters.forEach((userId, set) ->
                set.forEach(emitter -> send(userId, emitter, () -> SseEmitter.event().comment("heartbeat"))));
    }

    public int connectionCount(Long userId) {
        return emitters.getOrDefault(userId, Set.of()).size();
    }

    /** Closes all connections, e.g. on shutdown. */
    @PreDestroy
    public void completeAll() {
        emitters.forEach((userId, set) -> set.forEach(SseEmitter::complete));
        emitters.clear();
    }

    protected SseEmitter newEmitter() {
        return new SseEmitter(TIMEOUT_MS);
    }

    // an event builder can be built only once, so each connection gets a fresh one
    private void send(Long userId, SseEmitter emitter, Supplier<SseEventBuilder> event) {
        try {
            emitter.send(event.get());
        } catch (IOException | IllegalStateException ex) {
            log.debug("Dropping SSE connection of user {}: {}", userId, ex.getMessage());
            remove(userId, emitter);
            emitter.completeWithError(ex);
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        emitters.computeIfPresent(userId, (id, set) -> {
            set.remove(emitter);
            return set.isEmpty() ? null : set;
        });
    }
}
