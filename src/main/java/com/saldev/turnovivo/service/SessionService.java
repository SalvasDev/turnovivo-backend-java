package com.saldev.turnovivo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class SessionService {

    private static final String SESSION_PREFIX = "session:";

    private final RedisTemplate<String, String> redisTemplate;
    private final Duration sessionTtl;

    public SessionService(
            RedisTemplate<String, String> redisTemplate,
            @Value("${app.jwt.expiration-ms}") long expirationMs
    ) {
        this.redisTemplate = redisTemplate;
        this.sessionTtl = Duration.ofMillis(expirationMs);
    }

    public void activate(String userId) {
        redisTemplate.opsForValue().set(key(userId), "active", sessionTtl);
    }

    public boolean isActive(String userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(userId)));
    }

    public void revoke(String userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(String userId) {
        return SESSION_PREFIX + userId;
    }
}
