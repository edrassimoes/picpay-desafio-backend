package br.com.edras.picpaysimplificado.service;

import br.com.edras.picpaysimplificado.entity.MerchantUser;
import br.com.edras.picpaysimplificado.entity.Transaction;
import br.com.edras.picpaysimplificado.entity.User;
import br.com.edras.picpaysimplificado.entity.Wallet;
import br.com.edras.picpaysimplificado.entity.enums.TransactionStatus;
import br.com.edras.picpaysimplificado.entity.enums.UserType;
import br.com.edras.picpaysimplificado.dto.transaction.TransactionRequestDTO;
import br.com.edras.picpaysimplificado.dto.transaction.TransactionResponseDTO;
import br.com.edras.picpaysimplificado.event.TransactionCompletedEvent;
import br.com.edras.picpaysimplificado.exception.transaction.MerchantCannotTransferException;
import br.com.edras.picpaysimplificado.exception.transaction.SameUserTransactionException;
import br.com.edras.picpaysimplificado.exception.transaction.TransactionNotAuthorizedException;
import br.com.edras.picpaysimplificado.exception.transaction.TransactionNotFoundException;
import br.com.edras.picpaysimplificado.exception.user.UserNotFoundException;
import br.com.edras.picpaysimplificado.idempotency.IdempotencyKey;
import br.com.edras.picpaysimplificado.idempotency.IdempotencyService;
import br.com.edras.picpaysimplificado.idempotency.enums.RequestStatus;
import br.com.edras.picpaysimplificado.repository.TransactionRepository;
import br.com.edras.picpaysimplificado.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

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

    public TransactionService(TransactionRepository transactionRepository, UserRepository userRepository, WalletService walletService, AuthorizationService authorizationService, IdempotencyService idempotencyService, ApplicationEventPublisher eventPublisher, ObjectMapper objectMapper) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.walletService = walletService;
        this.authorizationService = authorizationService;
        this.idempotencyService = idempotencyService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    private void merchantDeposit(MerchantUser merchantUser, Double amount) {
        Wallet wallet = walletService.getWalletByUserId(merchantUser.getId());
        wallet.setBalance(wallet.getBalance() + amount);
        walletService.createOrUpdateWallet(wallet);
    }

    private User getPayer(Long payerId) {
        User payer = userRepository.findById(payerId)
                .orElseThrow(() -> new UserNotFoundException(payerId));

        if (payer.getUserType() == UserType.MERCHANT) {
            throw new MerchantCannotTransferException(payer.getId());
        }

        return payer;
    }

    private User getPayee(Long payeeId) {
        return userRepository.findById(payeeId)
                .orElseThrow(() -> new UserNotFoundException(payeeId));
    }

    private Transaction createTransaction(User payer, User payee, Double amount) {
        Transaction transaction = new Transaction();
        transaction.setAmount(amount);
        transaction.setPayer(payer);
        transaction.setPayee(payee);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setStatus(TransactionStatus.PENDING);
        return transactionRepository.save(transaction);
    }

    private void processTransfer(User payer, User payee, Double amount) {
        walletService.withdraw(payer.getId(), amount);

        if (payee instanceof MerchantUser) {
            merchantDeposit((MerchantUser) payee, amount);
        } else {
            walletService.deposit(payee.getId(), amount);
        }
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
                throw new RuntimeException("Failed to deserialize idempotent response", e);
            }
        }

        if (idempotencyKey.getStatus() == RequestStatus.PROCESSING && idempotencyKey.getResponse() == null) {
            throw new IllegalStateException("Request with this Idempotency-Key is already being processed");
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
            throw new RuntimeException("Failed to serialize idempotent response", e);
        }

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
