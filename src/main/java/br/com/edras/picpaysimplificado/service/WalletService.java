package br.com.edras.picpaysimplificado.service;

import br.com.edras.picpaysimplificado.entity.User;
import br.com.edras.picpaysimplificado.entity.Wallet;
import br.com.edras.picpaysimplificado.entity.enums.UserType;
import br.com.edras.picpaysimplificado.exception.user.UserNotFoundException;
import br.com.edras.picpaysimplificado.exception.wallet.InsufficientBalanceException;
import br.com.edras.picpaysimplificado.exception.wallet.InvalidAmountException;
import br.com.edras.picpaysimplificado.exception.wallet.MerchantCannotDepositException;
import br.com.edras.picpaysimplificado.exception.wallet.WalletNotFoundException;
import br.com.edras.picpaysimplificado.repository.UserRepository;
import br.com.edras.picpaysimplificado.repository.WalletRepository;
import io.micrometer.core.instrument.Counter;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class WalletService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final Counter transfersDeniedInsufficientFunds;

    public WalletService(WalletRepository walletRepository, UserRepository userRepository, Counter transfersDeniedInsufficientFunds) {
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
        this.transfersDeniedInsufficientFunds = transfersDeniedInsufficientFunds;
    }

    public Wallet createOrUpdateWallet(Wallet wallet) {
        return walletRepository.save(wallet);
    }

    public Wallet getWalletByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        Wallet wallet = user.getWallet();
        if (wallet == null) {
            throw new WalletNotFoundException(userId);
        }
        return wallet;
    }

    @Transactional
    public Wallet deposit(Long userId, Double amount) {
        if (amount <= 0){
            throw new InvalidAmountException(amount);
        }
        Wallet wallet = getWalletByUserId(userId);
        if (wallet.getUser().getUserType() == UserType.MERCHANT) {
            throw new MerchantCannotDepositException();
        }
        wallet.setBalance(wallet.getBalance() + amount);
        return wallet;
    }

    @Transactional
    public Wallet withdraw(Long userId, Double amount) {
        if (amount <= 0){
            throw new InvalidAmountException(amount);
        }
        Wallet wallet = getWalletByUserId(userId);
        if (wallet.getBalance() < amount) {
            transfersDeniedInsufficientFunds.increment();
            throw new InsufficientBalanceException();
        }
        wallet.setBalance(wallet.getBalance() - amount);
        return wallet;
    }

}
