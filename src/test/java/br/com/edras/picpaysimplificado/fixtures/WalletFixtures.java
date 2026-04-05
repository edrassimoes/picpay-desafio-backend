package br.com.edras.picpaysimplificado.fixtures;

import br.com.edras.picpaysimplificado.entity.User;
import br.com.edras.picpaysimplificado.entity.Wallet;

import java.math.BigDecimal;

public class WalletFixtures {

    public static Wallet createWallet(User user) {
        Wallet wallet = new Wallet();
        wallet.setUser(user);
        wallet.setBalance(new BigDecimal("100.00"));
        user.setWallet(wallet);
        return wallet;
    }

    public static Wallet createWalletWithInitialBalance(User user, BigDecimal balance) {
        Wallet wallet = new Wallet();
        wallet.setUser(user);
        wallet.setBalance(balance);
        user.setWallet(wallet);
        return wallet;
    }

}
