package br.com.edras.picpaysimplificado.controller;

import br.com.edras.picpaysimplificado.entity.Wallet;
import br.com.edras.picpaysimplificado.exception.user.UserNotFoundException;
import br.com.edras.picpaysimplificado.exception.wallet.MerchantCannotDepositException;
import br.com.edras.picpaysimplificado.service.WalletService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

@WebMvcTest(controllers = WalletController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
public class WalletControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public WalletService walletService() {
            return Mockito.mock(WalletService.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WalletService walletService;

    @Test
    void getWalletByUserId_ShouldReturn200_WhenUserExists() throws Exception {
        Wallet wallet = new Wallet();
        wallet.setId(1L);
        wallet.setBalance(new BigDecimal("1000.00"));

        given(walletService.getWalletByUserId(1L))
                .willReturn(wallet);

        mockMvc.perform(get("/wallets/user/{userId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.balance").value(1000));
    }

    @Test
    void getWalletByUserId_ShouldReturn404_WhenUserDoesNotExist() throws Exception {
        given(walletService.getWalletByUserId(99L))
                .willThrow(new UserNotFoundException("Usuário não encontrado com ID: " + 99L));

        mockMvc.perform(get("/wallets/user/{userId}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Usuário não encontrado com ID: " + 99L));
    }

    @Test
    void deposit_ShouldReturn200_WhenValidAmount() throws Exception {
        Wallet wallet = new Wallet();
        wallet.setId(1L);
        wallet.setBalance(new BigDecimal("1100.00"));

        given(walletService.deposit(eq(1L), any(BigDecimal.class)))
                .willReturn(wallet);

        mockMvc.perform(patch("/wallets/user/{userId}/deposit", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                { "amount": 100 }
            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1100));
    }

    @Test
    void deposit_ShouldReturn400_WhenAmountIsZero() throws Exception {
        mockMvc.perform(patch("/wallets/user/{userId}/deposit", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                { "amount": 0 }
            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Erro de validação: must be greater than 0"));
    }

    @Test
    void deposit_ShouldReturn403_WhenUserIsMerchant() throws Exception {
        given(walletService.deposit(anyLong(), any()))
                .willThrow(new MerchantCannotDepositException());

        mockMvc.perform(patch("/wallets/user/{userId}/deposit", 4L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                { "amount": 100 }
            """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("Operação inválida para usuários do tipo Lojista"));
    }

}
