package br.com.edras.picpaysimplificado.exception.transaction;

public class IdempotencyConflictException extends RuntimeException {
  public IdempotencyConflictException(String key) {
    super("A requisição com a chave '" + key + "' já está sendo processada");
  }
}
