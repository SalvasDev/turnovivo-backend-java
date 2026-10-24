package com.saldev.turnovivo.config;

import com.saldev.turnovivo.service.AppointmentService;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
public class RedisExpirationListener implements MessageListener {

    private static final String HOLD_PREFIX = "hold:";
    private final AppointmentService appointmentService;

    /**
     * Enterprise pattern: the constructor uses the @Lazy annotation.
     * This cleanly breaks the circular dependency.
     * Spring Boot initializes the listener immediately and loads the service on demand.
     */
    public RedisExpirationListener(@Lazy AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    /**
     * Required method from the MessageListener interface.
     * Spring invokes it automatically whenever a Redis key expires.
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = new String(message.getBody());

        if (expiredKey.startsWith(HOLD_PREFIX)) {
            String slotId = expiredKey.substring(HOLD_PREFIX.length());

            // Delegate expiration handling and waitlist promotion asynchronously.
            appointmentService.handleHoldExpiration(slotId);
        }
    }

    /**
     * This alternative method is required by the configured MessageListenerAdapter
     * and serves as a fallback reflective signature.
     */
    public void handleExpiredKey(String expiredKey) {
        if (expiredKey != null && expiredKey.startsWith(HOLD_PREFIX)) {
            String slotId = expiredKey.substring(HOLD_PREFIX.length());
            appointmentService.handleHoldExpiration(slotId);
        }
    }
}
