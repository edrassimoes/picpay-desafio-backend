package br.com.edras.picpaysimplificado.dto.transaction;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public class TransactionRequestDTO {

    @NotNull
    @Positive
    @DecimalMin(value = "0.01", message = "O valor mínimo de transferência é R$ 0,01")
    private BigDecimal amount;

    @NotNull
    private Long payerId;

    @NotNull
    private Long payeeId;

    public TransactionRequestDTO() {}

    public TransactionRequestDTO(BigDecimal amount, Long payerId, Long payeeId) {
        this.amount = amount;
        this.payerId = payerId;
        this.payeeId = payeeId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Long getPayerId() {
        return payerId;
    }

    public void setPayerId(Long payerId) {
        this.payerId = payerId;
    }

    public Long getPayeeId() {
        return payeeId;
    }

    public void setPayeeId(Long payeeId) {
        this.payeeId = payeeId;
    }

}
