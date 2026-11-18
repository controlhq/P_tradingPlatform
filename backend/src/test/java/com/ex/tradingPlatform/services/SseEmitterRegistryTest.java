package com.ex.tradingPlatform.services;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SseEmitterRegistryTest {

    private final List<RecordingEmitter> created = new ArrayList<>();

    private final SseEmitterRegistry registry = new SseEmitterRegistry() {
        @Override
        protected SseEmitter newEmitter() {
            RecordingEmitter emitter = new RecordingEmitter();
            created.add(emitter);
            return emitter;
        }
    };

    record Payload(long orderId, String status) {
    }

    @Test
    void register_tracksConnectionsPerUser() {
        registry.register(1L);
        registry.register(1L);
        registry.register(2L);

        assertThat(registry.connectionCount(1L)).isEqualTo(2);
        assertThat(registry.connectionCount(2L)).isEqualTo(1);
        assertThat(registry.connectionCount(3L)).isZero();
    }

    @Test
    void sendToUser_reachesAllConnectionsOfThatUserOnly() {
        RecordingEmitter tab1 = register(1L);
        RecordingEmitter tab2 = register(1L);
        RecordingEmitter other = register(2L);

        registry.sendToUser(1L, "order", new Payload(7, "FILLED"));

        assertThat(tab1.text()).isEqualTo("event:order\ndata:Payload[orderId=7, status=FILLED]\n\n");
        assertThat(tab2.text()).isEqualTo(tab1.text());
        assertThat(other.text()).isEmpty();
    }

    @Test
    void broadcast_reachesEveryConnection() {
        RecordingEmitter a = register(1L);
        RecordingEmitter b = register(2L);

        registry.broadcast("price", "AAPL 101");

        assertThat(a.text()).isEqualTo("event:price\ndata:AAPL 101\n\n");
        assertThat(b.text()).isEqualTo(a.text());
    }

    @Test
    void failedSend_dropsOnlyTheBrokenConnection() {
        RecordingEmitter broken = register(1L);
        RecordingEmitter healthy = register(1L);
        broken.failing = true;

        registry.sendToUser(1L, "order", "first");
        registry.sendToUser(1L, "order", "second");

        assertThat(registry.connectionCount(1L)).isEqualTo(1);
        assertThat(healthy.text()).contains("data:first").contains("data:second");
        assertThat(broken.attempts).isEqualTo(1);
    }

    @Test
    void heartbeat_sendsCommentToEveryConnection() {
        RecordingEmitter a = register(1L);
        RecordingEmitter b = register(2L);

        registry.heartbeat();

        assertThat(a.text()).isEqualTo(":heartbeat\n\n");
        assertThat(b.text()).isEqualTo(":heartbeat\n\n");
    }

    @Test
    void heartbeat_dropsDeadConnections() {
        register(1L).failing = true;

        registry.heartbeat();

        assertThat(registry.connectionCount(1L)).isZero();
    }

    @Test
    void completeAll_removesEveryConnection() {
        register(1L);
        register(2L);

        registry.completeAll();

        assertThat(registry.connectionCount(1L)).isZero();
        assertThat(registry.connectionCount(2L)).isZero();
    }

    private RecordingEmitter register(Long userId) {
        registry.register(userId);
        return created.getLast();
    }

    /** Captures what would be written to the client instead of needing a servlet response. */
    static class RecordingEmitter extends SseEmitter {

        final List<Set<DataWithMediaType>> sent = new ArrayList<>();
        boolean failing;
        int attempts;

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            attempts++;
            if (failing) {
                throw new IOException("client went away");
            }
            sent.add(builder.build());
        }

        String text() {
            return sent.stream()
                    .flatMap(Set::stream)
                    .map(d -> String.valueOf(d.getData()))
                    .collect(Collectors.joining());
        }
    }
}
