package br.com.edras.picpaysimplificado.exception.wallet;

import java.math.BigDecimal;

public class InvalidAmountException extends RuntimeException {
    public InvalidAmountException(BigDecimal amount) {
        super("Valor inválido: R$" + amount);
    }
}
