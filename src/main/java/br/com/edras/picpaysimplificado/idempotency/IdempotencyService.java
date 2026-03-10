package br.com.edras.picpaysimplificado.idempotency;

import br.com.edras.picpaysimplificado.idempotency.enums.RequestStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;

    public IdempotencyService(IdempotencyKeyRepository repository) {
        this.repository = repository;
    }

    public IdempotencyKey createKey(String key) {

        try {
            IdempotencyKey entity = new IdempotencyKey(key, RequestStatus.PROCESSING, null, LocalDateTime.now());
            return repository.save(entity);
        } catch (DataIntegrityViolationException e) {
            return repository.findById(key).orElseThrow();
        }

    }

    public IdempotencyKey save(IdempotencyKey key) {
        return repository.save(key);
    }
}