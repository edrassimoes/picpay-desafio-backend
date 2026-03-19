package br.com.edras.picpaysimplificado.exception.transaction;

public class IdempotencySerializationException extends RuntimeException {
    public IdempotencySerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
