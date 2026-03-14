package br.com.edras.picpaysimplificado.fixtures;

import br.com.edras.picpaysimplificado.idempotency.IdempotencyKey;
import br.com.edras.picpaysimplificado.idempotency.enums.RequestStatus;

public class IdempotencyKeyFixtures {

    public static IdempotencyKey completedKey() {
        return new IdempotencyKey(
                "test-key",
                RequestStatus.COMPLETED,
                "{}",
                null
        );
    }

    public static IdempotencyKey processingKey() {
        return new IdempotencyKey(
                "test-key",
                RequestStatus.PROCESSING,
                null,
                null
        );
    }

    public static IdempotencyKey nullStatusKey() {
        return new IdempotencyKey(
                "test-key",
                null,
                null,
                null
        );
    }

}