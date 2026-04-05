package br.com.edras.picpaysimplificado.controller;

import br.com.edras.picpaysimplificado.entity.enums.TransactionStatus;
import br.com.edras.picpaysimplificado.dto.transaction.TransactionRequestDTO;
import br.com.edras.picpaysimplificado.dto.transaction.TransactionResponseDTO;
import br.com.edras.picpaysimplificado.exception.transaction.IdempotencyConflictException;
import br.com.edras.picpaysimplificado.exception.transaction.MerchantCannotTransferException;
import br.com.edras.picpaysimplificado.exception.transaction.SameUserTransactionException;
import br.com.edras.picpaysimplificado.exception.transaction.TransactionNotAuthorizedException;
import br.com.edras.picpaysimplificado.exception.transaction.TransactionNotFoundException;
import br.com.edras.picpaysimplificado.exception.user.UserNotFoundException;
import br.com.edras.picpaysimplificado.service.TransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TransactionController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
class TransactionControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public TransactionService transactionService() {
            return Mockito.mock(TransactionService.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionService transactionService;

    @Test
    void createTransaction_ShouldReturn201_WhenRequestIsValid() throws Exception {
        TransactionRequestDTO request = new TransactionRequestDTO(100.0, 1L, 2L);
        TransactionResponseDTO response = new TransactionResponseDTO(
                1L, 1L, "Payer", 2L, "Payee", 100.0, LocalDateTime.now(), TransactionStatus.COMPLETED);

        when(transactionService.transfer(any(String.class), any(TransactionRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/transactions")
                        .header("Idempotency-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(new BigDecimal("100.0")))
                .andExpect(jsonPath("$.payerId").value(1L))
                .andExpect(jsonPath("$.payeeId").value(2L));
    }

    @Test
    void createTransaction_ShouldReturn400_WhenAmountIsInvalid() throws Exception {
        String invalidRequest = """
        {
            "payerId": 1,
            "payeeId": 2,
            "amount": 0
        }
    """;

        mockMvc.perform(post("/transactions")
                        .header("Idempotency-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTransaction_ShouldReturn403_WhenPayerIsMerchant() throws Exception {
        String request = """
        {
            "payerId": 1,
            "payeeId": 2,
            "amount": 100
        }
    """;

        when(transactionService.transfer(any(String.class), any(TransactionRequestDTO.class)))
                .thenThrow(new MerchantCannotTransferException(1L));

        mockMvc.perform(post("/transactions")
                        .header("Idempotency-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTransaction_ShouldReturn400_WhenPayerAndPayeeAreSame() throws Exception {
        String invalidRequest = """
        {
            "payerId": 1,
            "payeeId": 1,
            "amount": 100
        }
    """;

        when(transactionService.transfer(any(String.class), any(TransactionRequestDTO.class)))
                .thenThrow(new SameUserTransactionException());

        mockMvc.perform(post("/transactions")
                        .header("Idempotency-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTransaction_ShouldReturn404_WhenPayerDoesNotExist() throws Exception {
        String invalidRequest = """
        {
            "payerId": 99,
            "payeeId": 1,
            "amount": 100
        }
    """;

        when(transactionService.transfer(any(String.class), any(TransactionRequestDTO.class)))
                .thenThrow(new UserNotFoundException(99L));

        mockMvc.perform(post("/transactions")
                        .header("Idempotency-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isNotFound());
    }

    @Test
    void createTransaction_ShouldReturn403_WhenTransactionIsNotAuthorized() throws Exception {
        String request = """
        {
            "payerId": 99,
            "payeeId": 1,
            "amount": 100
        }
    """;

        when(transactionService.transfer(any(String.class), any(TransactionRequestDTO.class)))
                .thenThrow(new TransactionNotAuthorizedException());

        mockMvc.perform(post("/transactions")
                        .header("Idempotency-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTransaction_ShouldReturn400_WhenAmountIsNegative() throws Exception {
        String invalidRequest = """
        {
            "payerId": 1,
            "payeeId": 2,
            "amount": -100
        }
    """;

        mockMvc.perform(post("/transactions")
                        .header("Idempotency-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTransaction_ShouldReturn409_WhenIdempotencyKeyIsProcessing() throws Exception {
        String request = """
        {
            "payerId": 1,
            "payeeId": 2,
            "amount": 100
        }
    """;

        when(transactionService.transfer(any(String.class), any(TransactionRequestDTO.class)))
                .thenThrow(new IdempotencyConflictException("test-key"));

        mockMvc.perform(post("/transactions")
                        .header("Idempotency-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict());
    }

    @Test
    void createTransaction_ShouldReturn201_WhenIdempotencyKeyAlreadyCompleted() throws Exception {
        TransactionRequestDTO request = new TransactionRequestDTO(100.0, 1L, 2L);
        TransactionResponseDTO cachedResponse = new TransactionResponseDTO(
                1L, 1L, "Payer", 2L, "Payee", 100.0, LocalDateTime.now(), TransactionStatus.COMPLETED);

        when(transactionService.transfer(any(String.class), any(TransactionRequestDTO.class)))
                .thenReturn(cachedResponse);

        mockMvc.perform(post("/transactions")
                        .header("Idempotency-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").value(1L))
                .andExpect(jsonPath("$.amount").value(new BigDecimal("100.0")));
    }

    @Test
    void findById_ShouldReturn200_WhenTransactionExists() throws Exception {
        TransactionResponseDTO response = new TransactionResponseDTO(
                1L, 1L, "Payer", 2L, "Payee", 100.0, LocalDateTime.now(), TransactionStatus.COMPLETED);

        when(transactionService.findById(1L)).thenReturn(response);

        mockMvc.perform(get("/transactions/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(1L));
    }

    @Test
    void findById_ShouldReturn404_WhenTransactionDoesNotExist() throws Exception {
        when(transactionService.findById(99L)).thenThrow(new TransactionNotFoundException(99L));

        mockMvc.perform(get("/transactions/{id}", 99L))
                .andExpect(status().isNotFound());
    }

    @Test
    void findTransactionsByUserId_ShouldReturn200_WithTransactionList() throws Exception {
        List<TransactionResponseDTO> response = List.of(
                new TransactionResponseDTO(
                        1L, 1L, "Payer", 2L, "Payee", 100.0, LocalDateTime.now(), TransactionStatus.COMPLETED),
                new TransactionResponseDTO(
                        1L, 1L, "Payer", 3L, "Payee", 250.0, LocalDateTime.now(), TransactionStatus.PENDING)
        );

        when(transactionService.findTransactionsByUserId(1L)).thenReturn(response);

        mockMvc.perform(get("/transactions/users/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void findTransactionsByUserId_ShouldReturn200_WhenNoTransactionsExist() throws Exception {
        when(transactionService.findTransactionsByUserId(1L)).thenReturn(List.of());

        mockMvc.perform(get("/transactions/users/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

}
