package br.com.edras.picpaysimplificado.service;

import br.com.edras.picpaysimplificado.entity.Transaction;
import br.com.edras.picpaysimplificado.entity.User;
import br.com.edras.picpaysimplificado.entity.enums.TransactionStatus;
import br.com.edras.picpaysimplificado.entity.enums.UserType;
import br.com.edras.picpaysimplificado.dto.transaction.TransactionRequestDTO;
import br.com.edras.picpaysimplificado.dto.transaction.TransactionResponseDTO;
import br.com.edras.picpaysimplificado.event.TransactionCompletedEvent;
import br.com.edras.picpaysimplificado.exception.transaction.*;
import br.com.edras.picpaysimplificado.exception.user.UserNotFoundException;
import br.com.edras.picpaysimplificado.idempotency.IdempotencyKey;
import br.com.edras.picpaysimplificado.idempotency.IdempotencyService;
import br.com.edras.picpaysimplificado.idempotency.enums.RequestStatus;
import br.com.edras.picpaysimplificado.repository.TransactionRepository;
import br.com.edras.picpaysimplificado.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import jakarta.transaction.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final AuthorizationService authorizationService;
    private final IdempotencyService idempotencyService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final Counter transfersSuccess;
    private final Counter transfersFailed;
    private final Counter transfersAborted;
    private final Counter transfersDeniedInsufficientFunds;
    private final Counter transfersDeniedInvalidPayer;

    public TransactionService(TransactionRepository transactionRepository, UserRepository userRepository, WalletService walletService, AuthorizationService authorizationService, IdempotencyService idempotencyService, ApplicationEventPublisher eventPublisher, ObjectMapper objectMapper, Counter transfersSuccess, Counter transfersFailed, Counter transfersAborted, Counter transfersDeniedInsufficientFunds, Counter transfersDeniedInvalidPayer) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.walletService = walletService;
        this.authorizationService = authorizationService;
        this.idempotencyService = idempotencyService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.transfersSuccess = transfersSuccess;
        this.transfersFailed = transfersFailed;
        this.transfersAborted = transfersAborted;
        this.transfersDeniedInsufficientFunds = transfersDeniedInsufficientFunds;
        this.transfersDeniedInvalidPayer = transfersDeniedInvalidPayer;
    }

    private User getPayer(Long payerId) {
        User payer = userRepository.findById(payerId)
                .orElseThrow(() -> new UserNotFoundException(payerId));

        if (payer.getUserType() == UserType.MERCHANT) {
            transfersDeniedInvalidPayer.increment();
            throw new MerchantCannotTransferException(payer.getId());
        }

        return payer;
    }

    private User getPayee(Long payeeId) {
        return userRepository.findById(payeeId)
                .orElseThrow(() -> new UserNotFoundException(payeeId));
    }

    private Transaction createTransaction(User payer, User payee, BigDecimal amount) {
        Transaction transaction = new Transaction();
        transaction.setAmount(amount);
        transaction.setPayer(payer);
        transaction.setPayee(payee);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setStatus(TransactionStatus.PENDING);
        return transactionRepository.save(transaction);
    }

    private void processTransfer(User payer, User payee, BigDecimal amount) {
        walletService.withdraw(payer.getId(), amount);
        walletService.depositFromTransaction(payee.getId(), amount);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "transactions:user", key = "#dto.payerId"),
            @CacheEvict(value = "transactions:user", key = "#dto.payeeId")
    })
    public TransactionResponseDTO transfer(String key, TransactionRequestDTO dto) {

        IdempotencyKey idempotencyKey = idempotencyService.createKey(key);

        // Verifica o status da requisição
        if (idempotencyKey.getStatus() == RequestStatus.COMPLETED && idempotencyKey.getResponse() != null) {
            try {
                return objectMapper.readValue(idempotencyKey.getResponse(), TransactionResponseDTO.class);
            } catch (Exception e) {
                throw new IdempotencySerializationException("Failed to deserialize idempotent response", e);
            }
        }

        if (idempotencyKey.getStatus() == RequestStatus.PROCESSING && idempotencyKey.getResponse() == null) {
            throw new IdempotencyConflictException("Request with this Idempotency-Key is already being processed");
        }

        if (dto.getPayerId().equals(dto.getPayeeId())) {
            throw new SameUserTransactionException();
        }

        User payer = getPayer(dto.getPayerId());
        User payee = getPayee(dto.getPayeeId());

        Transaction savedTransaction = createTransaction(payer, payee, dto.getAmount());

        // API de autenticação
        TransactionStatus authStatus = authorizationService.authorize();

        if (authStatus != TransactionStatus.AUTHORIZED) {
            savedTransaction.setStatus(authStatus);
            transactionRepository.save(savedTransaction);
            idempotencyKey.setStatus(RequestStatus.COMPLETED);
            idempotencyService.save(idempotencyKey);
            transfersFailed.increment();
            throw new TransactionNotAuthorizedException();
        }

        // Realiza a transação
        processTransfer(payer, payee, dto.getAmount());

        savedTransaction.setStatus(TransactionStatus.COMPLETED);
        Transaction completedTransaction = transactionRepository.save(savedTransaction);

        // Publica um evento -> API de notificação
        eventPublisher.publishEvent(new TransactionCompletedEvent(completedTransaction));

        // Converte a resposta em JSON e salva no banco
        try {
            String jsonResponse = objectMapper.writeValueAsString(new TransactionResponseDTO(completedTransaction));
            idempotencyKey.setResponse(jsonResponse);
            idempotencyKey.setStatus(RequestStatus.COMPLETED);
            idempotencyService.save(idempotencyKey);
        } catch (Exception e) {
            transfersAborted.increment();
            throw new IdempotencySerializationException("Failed to serialize idempotent response", e);
        }

        transfersSuccess.increment();
        return new TransactionResponseDTO(completedTransaction);
    }

    @Cacheable(value = "transactions", key = "#id")
    public TransactionResponseDTO findById(Long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));
        return new TransactionResponseDTO(transaction);
    }

    @Cacheable(value = "transactions:user", key = "#userId")
    public List<TransactionResponseDTO> findTransactionsByUserId(Long userId) {
        List<Transaction> transactions = transactionRepository.findByUserId(userId);
        return transactions.stream().map(TransactionResponseDTO::new).toList();
    }

}
