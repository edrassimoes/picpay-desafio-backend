package br.com.edras.picpaysimplificado.dto.wallet;

import br.com.edras.picpaysimplificado.entity.Wallet;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public class AmountDTO {

    @Positive
    private BigDecimal amount;

    public AmountDTO() {}

    public AmountDTO(Wallet wallet) {
        this.amount = wallet.getBalance();
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

}
