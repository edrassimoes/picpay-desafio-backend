package br.com.edras.picpaysimplificado.service;

import br.com.edras.picpaysimplificado.entity.CommonUser;
import br.com.edras.picpaysimplificado.entity.MerchantUser;
import br.com.edras.picpaysimplificado.entity.Wallet;
import br.com.edras.picpaysimplificado.exception.user.UserNotFoundException;
import br.com.edras.picpaysimplificado.exception.wallet.InsufficientBalanceException;
import br.com.edras.picpaysimplificado.exception.wallet.InvalidAmountException;
import br.com.edras.picpaysimplificado.exception.wallet.MerchantCannotDepositException;
import br.com.edras.picpaysimplificado.exception.wallet.WalletNotFoundException;
import br.com.edras.picpaysimplificado.fixtures.CommonUserFixtures;
import br.com.edras.picpaysimplificado.fixtures.MerchantUserFixtures;
import br.com.edras.picpaysimplificado.fixtures.WalletFixtures;
import br.com.edras.picpaysimplificado.repository.UserRepository;
import br.com.edras.picpaysimplificado.repository.WalletRepository;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class WalletServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private Counter transfersDeniedInsufficientFunds;

    @InjectMocks
    private WalletService walletService;

    @Test
    void createOrUpdateWallet_Success() {
        CommonUser commonUser = CommonUserFixtures.createValidCommonUser();
        Wallet commonUserWallet = WalletFixtures.createWallet(commonUser);

        when(walletRepository.save(any(Wallet.class))).thenReturn(commonUserWallet);

        Wallet savedWallet = walletService.createOrUpdateWallet(commonUserWallet);

        assertNotNull(savedWallet);
        verify(walletRepository).save(commonUserWallet);
    }

    @Test
    void getWalletByUserId_Success() {
        CommonUser commonUser = CommonUserFixtures.createValidCommonUser();
        Wallet commonUserWallet = WalletFixtures.createWallet(commonUser);

        when(userRepository.findById(1L)).thenReturn(Optional.of(commonUser));

        Wallet foundWallet = walletService.getWalletByUserId(1L);

        assertNotNull(foundWallet);
        assertEquals(commonUserWallet, foundWallet);
    }

    @Test
    void getWalletByUserId_ThrowsUserNotFoundException() {
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> walletService.getWalletByUserId(99L));
    }

    @Test
    void getWalletByUserId_ThrowsWalletNotFoundException() {
        CommonUser commonUser = CommonUserFixtures.createValidCommonUser();

        when(userRepository.findById(1L)).thenReturn(Optional.of(commonUser));

        assertThrows(WalletNotFoundException.class, () -> walletService.getWalletByUserId(1L));
    }

    @Test
    void deposit_Success() {
        CommonUser commonUser = CommonUserFixtures.createValidCommonUser();
        Wallet commonUserWallet = WalletFixtures.createWallet(commonUser);

        when(userRepository.findById(1L)).thenReturn(Optional.of(commonUser));

        double initialBalance = commonUserWallet.getBalance();
        double depositAmount = 50.0;

        Wallet updatedWallet = walletService.deposit(1L, depositAmount);

        assertNotNull(updatedWallet);
        assertEquals(initialBalance + depositAmount, updatedWallet.getBalance());
    }

    @Test
    void deposit_ThrowsInvalidAmountException_ForZeroAmount() {
        assertThrows(InvalidAmountException.class, () -> walletService.deposit(1L, 0.0));
    }

    @Test
    void deposit_ThrowsInvalidAmountException_ForNegativeAmount() {
        assertThrows(InvalidAmountException.class, () -> walletService.deposit(1L, -50.0));
    }

    @Test
    void deposit_ThrowsMerchantCannotDepositException() {
        MerchantUser merchantUser = MerchantUserFixtures.createValidMerchantUser();
        Wallet merchantUserWallet = WalletFixtures.createWallet(merchantUser);

        when(userRepository.findById(1L)).thenReturn(Optional.of(merchantUser));

        assertThrows(MerchantCannotDepositException.class, () -> walletService.deposit(1L, 100.0));
    }

    @Test
    void withdraw_Success() {
        CommonUser commonUser = CommonUserFixtures.createValidCommonUser();
        Wallet commonUserWallet = WalletFixtures.createWallet(commonUser);

        when(userRepository.findById(1L)).thenReturn(Optional.of(commonUser));

        double initialBalance = commonUserWallet.getBalance();
        double withdrawAmount = 50.0;

        Wallet updatedWallet = walletService.withdraw(1L, withdrawAmount);

        assertNotNull(updatedWallet);
        assertEquals(initialBalance - withdrawAmount, updatedWallet.getBalance());
    }

    @Test
    void withdraw_ThrowsInvalidAmountException_ForZeroAmount() {
        assertThrows(InvalidAmountException.class, () -> walletService.withdraw(1L, 0.0));
    }

    @Test
    void withdraw_ThrowsInvalidAmountException_ForNegativeAmount() {
        assertThrows(InvalidAmountException.class, () -> walletService.withdraw(1L, -50.0));
    }

    @Test
    void withdraw_ThrowsInsufficientBalanceException() {
        CommonUser commonUser = CommonUserFixtures.createValidCommonUser();
        Wallet commonUserWallet = WalletFixtures.createWallet(commonUser);

        when(userRepository.findById(1L)).thenReturn(Optional.of(commonUser));

        double withdrawAmount = 200.0;

        assertThrows(InsufficientBalanceException.class, () -> walletService.withdraw(1L, withdrawAmount));
    }

}