package br.com.edras.picpaysimplificado.config;

import br.com.edras.picpaysimplificado.idempotency.IdempotencyKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, IdempotencyKey> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, IdempotencyKey> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(IdempotencyKey.class));
        return template;
    }
}
