package br.com.edras.picpaysimplificado.idempotency;

import br.com.edras.picpaysimplificado.idempotency.enums.RequestStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class IdempotencyService implements Serializable {

    private static final String REDIS_KEY_PREFIX = "idempotency:";
    private static final Duration TTL = Duration.ofHours(24);

    private final IdempotencyKeyRepository repository;
    private final RedisTemplate<String, IdempotencyKey> redisTemplate;

    public IdempotencyService(IdempotencyKeyRepository repository,
                               RedisTemplate<String, IdempotencyKey> redisTemplate) {
        this.repository = repository;
        this.redisTemplate = redisTemplate;
    }

    public IdempotencyKey createKey(String key) {
        String redisKey = REDIS_KEY_PREFIX + key;

        IdempotencyKey cached = redisTemplate.opsForValue().get(redisKey);
        if (cached != null) {
            return cached;
        }

        try {
            IdempotencyKey entity = new IdempotencyKey(key, RequestStatus.PROCESSING, null, LocalDateTime.now());
            IdempotencyKey saved = repository.save(entity);
            redisTemplate.opsForValue().set(redisKey, saved, TTL);
            return saved;
        } catch (DataIntegrityViolationException e) {
            IdempotencyKey existing = repository.findById(key).orElseThrow();
            redisTemplate.opsForValue().set(redisKey, existing, TTL);
            return existing;
        }
    }

    public IdempotencyKey save(IdempotencyKey idempotencyKey) {
        IdempotencyKey saved = repository.save(idempotencyKey);
        String redisKey = REDIS_KEY_PREFIX + idempotencyKey.getKey();
        redisTemplate.opsForValue().set(redisKey, saved, TTL);
        return saved;
    }
}