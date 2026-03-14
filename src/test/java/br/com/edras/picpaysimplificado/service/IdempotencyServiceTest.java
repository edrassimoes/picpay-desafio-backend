package br.com.edras.picpaysimplificado.service;

import br.com.edras.picpaysimplificado.idempotency.IdempotencyKey;
import br.com.edras.picpaysimplificado.idempotency.IdempotencyKeyRepository;
import br.com.edras.picpaysimplificado.idempotency.IdempotencyService;
import br.com.edras.picpaysimplificado.idempotency.enums.RequestStatus;
import br.com.edras.picpaysimplificado.fixtures.IdempotencyKeyFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyKeyRepository idempotencyRepository;

    @Mock
    private RedisTemplate<String, IdempotencyKey> redisTemplate;

    @Mock
    private ValueOperations<String, IdempotencyKey> valueOperations;

    @InjectMocks
    private IdempotencyService idempotencyService;

    @Test
    void createKey_WhenKeyExistsInRedis_ShouldReturnCachedKey() {
        String keyValue = "cached-key";
        IdempotencyKey cachedKey = IdempotencyKeyFixtures.completedKey();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("idempotency:" + keyValue)).thenReturn(cachedKey);

        IdempotencyKey result = idempotencyService.createKey(keyValue);

        assertThat(result).isEqualTo(cachedKey);
        verifyNoInteractions(idempotencyRepository);
    }

    @Test
    void createKey_WhenKeyDoesNotExist_ShouldCreateAndCacheNewKey() {
        String keyValue = "new-key";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("idempotency:" + keyValue)).thenReturn(null);
        when(idempotencyRepository.save(any(IdempotencyKey.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IdempotencyKey result = idempotencyService.createKey(keyValue);

        assertThat(result).isNotNull();
        assertThat(result.getKey()).isEqualTo(keyValue);
        assertThat(result.getStatus()).isEqualTo(RequestStatus.PROCESSING);

        verify(idempotencyRepository).save(any(IdempotencyKey.class));
        verify(valueOperations).set(eq("idempotency:" + keyValue), any(IdempotencyKey.class), any());
    }

    @Test
    void createKey_WhenConcurrentRequest_ShouldFallbackToDatabaseAndCache() {
        String keyValue = "existing-key";
        IdempotencyKey existingKey = IdempotencyKeyFixtures.completedKey();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("idempotency:" + keyValue)).thenReturn(null);
        when(idempotencyRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(idempotencyRepository.findById(keyValue))
                .thenReturn(Optional.of(existingKey));

        IdempotencyKey result = idempotencyService.createKey(keyValue);

        assertThat(result).isEqualTo(existingKey);
        verify(valueOperations).set(eq("idempotency:" + keyValue), eq(existingKey), any());
    }

    @Test
    void save_ShouldPersistToDatabaseAndUpdateCache() {
        IdempotencyKey key = IdempotencyKeyFixtures.completedKey();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(idempotencyRepository.save(key)).thenReturn(key);

        IdempotencyKey result = idempotencyService.save(key);

        assertThat(result).isEqualTo(key);
        verify(idempotencyRepository).save(key);
        verify(valueOperations).set(eq("idempotency:" + key.getKey()), eq(key), any());
    }

}
