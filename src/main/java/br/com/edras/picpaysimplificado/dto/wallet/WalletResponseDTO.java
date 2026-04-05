package br.com.edras.picpaysimplificado.dto.wallet;

import br.com.edras.picpaysimplificado.entity.Wallet;

import java.math.BigDecimal;

public class WalletResponseDTO {

    private Long id;
    private BigDecimal balance;

    public WalletResponseDTO() {}

    public WalletResponseDTO(Wallet wallet) {
        this.id = wallet.getId();
        this.balance = wallet.getBalance();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

}
