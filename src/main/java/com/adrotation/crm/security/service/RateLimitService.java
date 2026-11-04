package com.adrotation.crm.security.service;

import com.adrotation.crm.shared.exception.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${app.security.rate-limit.login-attempts:8}") private int loginAttempts;
    @Value("${app.security.rate-limit.register-attempts:4}") private int registerAttempts;
    @Value("${app.security.rate-limit.code-attempts:10}") private int codeAttempts;
    @Value("${app.security.rate-limit.window-minutes:15}") private long windowMinutes;

    public void login(String key) { check("login", key, loginAttempts); }
    public void register(String key) { check("register", key, registerAttempts); }
    public void code(String key) { check("code", key, codeAttempts); }

    private void check(String action, String key, int max) {
        Instant now = Instant.now();
        Duration window = Duration.ofMinutes(Math.max(1, windowMinutes));
        String composite = action + ':' + key;
        Bucket bucket = buckets.computeIfAbsent(composite, ignored -> new Bucket(now, 0));
        synchronized (bucket) {
            if (Duration.between(bucket.startedAt, now).compareTo(window) >= 0) {
                bucket.startedAt = now;
                bucket.count = 0;
            }
            bucket.count++;
            if (bucket.count > Math.max(1, max)) {
                long elapsed = Math.max(0, Duration.between(bucket.startedAt, now).getSeconds());
                long retry = Math.max(1, window.toSeconds() - elapsed);
                throw new RateLimitExceededException("Слишком много попыток. Повторите позже.", retry);
            }
        }
        if (buckets.size() > 20_000) cleanup(now.minus(window.multipliedBy(2)));
    }

    private void cleanup(Instant olderThan) {
        buckets.entrySet().removeIf(e -> e.getValue().startedAt.isBefore(olderThan));
    }

    private static final class Bucket {
        private Instant startedAt;
        private int count;
        private Bucket(Instant startedAt, int count) { this.startedAt = startedAt; this.count = count; }
    }
}
