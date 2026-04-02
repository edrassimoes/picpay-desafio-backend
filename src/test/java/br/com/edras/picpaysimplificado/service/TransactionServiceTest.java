package br.com.edras.picpaysimplificado.service;

import br.com.edras.picpaysimplificado.entity.CommonUser;
import br.com.edras.picpaysimplificado.entity.MerchantUser;
import br.com.edras.picpaysimplificado.entity.Transaction;
import br.com.edras.picpaysimplificado.entity.User;
import br.com.edras.picpaysimplificado.entity.enums.TransactionStatus;
import br.com.edras.picpaysimplificado.dto.transaction.TransactionRequestDTO;
import br.com.edras.picpaysimplificado.dto.transaction.TransactionResponseDTO;
import br.com.edras.picpaysimplificado.exception.transaction.IdempotencyConflictException;
import br.com.edras.picpaysimplificado.exception.transaction.MerchantCannotTransferException;
import br.com.edras.picpaysimplificado.exception.transaction.SameUserTransactionException;
import br.com.edras.picpaysimplificado.exception.transaction.TransactionNotAuthorizedException;
import br.com.edras.picpaysimplificado.exception.transaction.TransactionNotFoundException;
import br.com.edras.picpaysimplificado.fixtures.CommonUserFixtures;
import br.com.edras.picpaysimplificado.fixtures.MerchantUserFixtures;
import br.com.edras.picpaysimplificado.fixtures.TransactionFixtures;
import br.com.edras.picpaysimplificado.fixtures.IdempotencyKeyFixtures;
import br.com.edras.picpaysimplificado.idempotency.IdempotencyKey;
import br.com.edras.picpaysimplificado.idempotency.IdempotencyService;
import br.com.edras.picpaysimplificado.repository.TransactionRepository;
import br.com.edras.picpaysimplificado.repository.UserRepository;
import br.com.edras.picpaysimplificado.event.TransactionCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Counter transfersSuccess;

    @Mock
    private Counter transfersFailed;

    @Mock
    private Counter transfersAborted;

    @Mock
    private Counter transfersDeniedInsufficientFunds;

    @Mock
    private Counter transfersDeniedInvalidPayer;

    @InjectMocks
    private TransactionService transactionService;

    @BeforeEach
    void setup() throws Exception {
        lenient().when(idempotencyService.createKey(any()))
                .thenReturn(IdempotencyKeyFixtures.nullStatusKey());

        lenient().doReturn("{}").when(objectMapper).writeValueAsString(any());
    }

    @Test
    void transfer_WhenSuccessful_ShouldCompleteTransaction() {
        Transaction transaction = TransactionFixtures.createTransaction(1L, 2L);
        User payer = transaction.getPayer();
        User payee = transaction.getPayee();

        TransactionRequestDTO requestDTO = new TransactionRequestDTO(50.00, payer.getId(), payee.getId());

        when(userRepository.findById(payer.getId())).thenReturn(Optional.of(payer));
        when(userRepository.findById(payee.getId())).thenReturn(Optional.of(payee));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(authorizationService.authorize()).thenReturn(TransactionStatus.AUTHORIZED);

        TransactionResponseDTO response = transactionService.transfer("test-key", requestDTO);

        assertThat(response).isNotNull();

        verify(walletService).withdraw(payer.getId(), requestDTO.getAmount());
        verify(walletService).deposit(payee.getId(), requestDTO.getAmount());
        verify(eventPublisher).publishEvent(any(TransactionCompletedEvent.class));
    }

    @Test
    void transfer_WhenPayerIsPayee_ShouldThrowException() {
        Long userId = 1L;

        TransactionRequestDTO requestDTO = new TransactionRequestDTO(50.00, userId, userId);

        assertThrows(SameUserTransactionException.class, () -> transactionService.transfer("test-key", requestDTO));

        verify(transactionRepository, never()).save(any());
        verify(walletService, never()).withdraw(any(), any());
        verify(walletService, never()).deposit(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void transfer_WhenPayerIsMerchant_ShouldThrowException() {
        MerchantUser payer = MerchantUserFixtures.createValidMerchantUser(1L);
        CommonUser payee = CommonUserFixtures.createValidCommonUser(2L);

        TransactionRequestDTO requestDTO = new TransactionRequestDTO(50.00, payer.getId(), payee.getId());

        when(userRepository.findById(payer.getId())).thenReturn(Optional.of(payer));

        assertThrows(MerchantCannotTransferException.class, () -> transactionService.transfer("test-key", requestDTO));

        verify(transactionRepository, never()).save(any());
        verify(walletService, never()).withdraw(any(), any());
        verify(walletService, never()).deposit(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void transfer_WhenTransactionNotAuthorized_ShouldThrowException() {
        Transaction transaction = TransactionFixtures.createTransaction(1L, 2L);
        User payer = transaction.getPayer();
        User payee = transaction.getPayee();

        TransactionRequestDTO requestDTO = new TransactionRequestDTO(50.00, payer.getId(), payee.getId());

        when(userRepository.findById(payer.getId())).thenReturn(Optional.of(payer));
        when(userRepository.findById(payee.getId())).thenReturn(Optional.of(payee));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(authorizationService.authorize()).thenReturn(TransactionStatus.FAILED);

        assertThrows(TransactionNotAuthorizedException.class, () -> {transactionService.transfer("test-key", requestDTO);});

        verify(walletService, never()).withdraw(any(), any());
        verify(walletService, never()).deposit(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void findById_WhenTransactionExists_ShouldReturnTransactionDTO() {
        Transaction transaction = TransactionFixtures.createTransaction();
        transaction.setId(1L);

        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));

        TransactionResponseDTO response = transactionService.findById(transaction.getId());

        assertThat(response).isNotNull();
    }

    @Test
    void findById_WhenTransactionDoesNotExist_ShouldThrowException() {
        Long transactionId = 99L;

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.empty());

        assertThrows(TransactionNotFoundException.class, () -> {
            transactionService.findById(transactionId);
        });
    }

    @Test
    void findTransactionsByUserId_WhenTransactionsExist_ShouldReturnListOfDTOs() {
        Long userId = 1L;

        Transaction transaction1 = TransactionFixtures.createTransaction();
        Transaction transaction2 = TransactionFixtures.createTransaction();

        List<Transaction> transactions = List.of(transaction1, transaction2);

        when(transactionRepository.findByUserId(userId)).thenReturn(transactions);

        List<TransactionResponseDTO> response = transactionService.findTransactionsByUserId(userId);

        assertThat(response).isNotNull();
        assertThat(response).hasSize(2);
    }

    @Test
    void findTransactionsByUserId_WhenNoTransactionsExist_ShouldReturnEmptyList() {
        Long userId = 1L;

        when(transactionRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        List<TransactionResponseDTO> response = transactionService.findTransactionsByUserId(userId);

        assertThat(response).isNotNull();
        assertThat(response).isEmpty();
    }

    @Test
    void transfer_WhenRequestAlreadyCompleted_ShouldReturnSavedResponse() throws Exception {
        TransactionRequestDTO requestDTO = new TransactionRequestDTO(50.00, 1L, 2L);

        IdempotencyKey key = IdempotencyKeyFixtures.completedKey();

        TransactionResponseDTO cachedResponse = mock(TransactionResponseDTO.class);

        when(idempotencyService.createKey(any())).thenReturn(key);
        doReturn(cachedResponse).when(objectMapper).readValue(any(String.class), eq(TransactionResponseDTO.class));

        TransactionResponseDTO response = transactionService.transfer("test-key", requestDTO);

        assertThat(response).isNotNull();
        verify(walletService, never()).withdraw(any(), any());
        verify(walletService, never()).deposit(any(), any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transfer_WhenRequestIsProcessing_ShouldThrowException() {
        TransactionRequestDTO requestDTO = new TransactionRequestDTO(50.00, 1L, 2L);

        IdempotencyKey key = IdempotencyKeyFixtures.processingKey();

        when(idempotencyService.createKey(any())).thenReturn(key);

        assertThrows(IdempotencyConflictException.class,
                () -> transactionService.transfer("test-key", requestDTO));

        verify(walletService, never()).withdraw(any(), any());
        verify(walletService, never()).deposit(any(), any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transfer_WhenSuccessful_ShouldPersistIdempotentResponse() {
        Transaction transaction = TransactionFixtures.createTransaction(1L, 2L);
        User payer = transaction.getPayer();
        User payee = transaction.getPayee();

        TransactionRequestDTO requestDTO = new TransactionRequestDTO(50.00, payer.getId(), payee.getId());

        IdempotencyKey key = IdempotencyKeyFixtures.nullStatusKey();

        when(userRepository.findById(payer.getId())).thenReturn(Optional.of(payer));
        when(userRepository.findById(payee.getId())).thenReturn(Optional.of(payee));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(authorizationService.authorize()).thenReturn(TransactionStatus.AUTHORIZED);

        transactionService.transfer("test-key", requestDTO);

        verify(idempotencyService).save(any());
    }

}