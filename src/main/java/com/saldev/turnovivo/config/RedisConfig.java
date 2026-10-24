package com.saldev.turnovivo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    // Default Redis Pub/Sub channel for capturing key expirations
    private static final String EXPIRED_CHANNEL_PATTERN = "__keyevent@0__:expired";

    /*
     * Configures the main CRUD client. Uses String for keys and values,
     * avoiding external dependencies on Jackson or ObjectMappers.
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use the standard string serializer so all data is stored as readable plain text.
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /*
     * Asynchronous container that orchestrates listening to the Redis Pub/Sub channel
     * without blocking the main thread of the HTTP application.
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter listenerAdapter) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // Link the adapter to the expiration channel pattern
        container.addMessageListener(listenerAdapter, new PatternTopic(EXPIRED_CHANNEL_PATTERN));
        return container;
    }

    /*
     * Adapter that receives the signal from Redis and redirects it to the method
     * responsible for processing the expiration in the service layer.
     */
    @Bean
    public MessageListenerAdapter listenerAdapter(RedisExpirationListener expirationListener) {
        // "handleExpiredKey" will be the target method in our listener
        return new MessageListenerAdapter(expirationListener, "handleExpiredKey");
    }
}
