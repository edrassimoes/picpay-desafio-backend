package br.com.edras.picpaysimplificado.service;

import br.com.edras.picpaysimplificado.client.AuthorizerClient;
import br.com.edras.picpaysimplificado.client.AuthorizerResponse;
import br.com.edras.picpaysimplificado.client.NotificationClient;
import br.com.edras.picpaysimplificado.client.NotificationRequest;
import br.com.edras.picpaysimplificado.domain.MerchantUser;
import br.com.edras.picpaysimplificado.domain.Transaction;
import br.com.edras.picpaysimplificado.domain.User;
import br.com.edras.picpaysimplificado.domain.Wallet;
import br.com.edras.picpaysimplificado.domain.enums.TransactionStatus;
import br.com.edras.picpaysimplificado.domain.enums.UserType;
import br.com.edras.picpaysimplificado.dto.transaction.TransactionRequestDTO;
import br.com.edras.picpaysimplificado.dto.transaction.TransactionResponseDTO;
import br.com.edras.picpaysimplificado.exception.transaction.MerchantCannotTransferException;
import br.com.edras.picpaysimplificado.exception.transaction.SameUserTransactionException;
import br.com.edras.picpaysimplificado.exception.transaction.TransactionNotAuthorizedException;
import br.com.edras.picpaysimplificado.exception.transaction.TransactionNotFoundException;
import br.com.edras.picpaysimplificado.exception.user.UserNotFoundException;
import br.com.edras.picpaysimplificado.repository.TransactionRepository;
import br.com.edras.picpaysimplificado.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final AuthorizerClient authorizerClient;
    private final NotificationClient notificationClient;

    public TransactionService(TransactionRepository transactionRepository, UserRepository userRepository, WalletService walletService, AuthorizerClient authorizerClient, NotificationClient notificationClient) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.walletService = walletService;
        this.authorizerClient = authorizerClient;
        this.notificationClient = notificationClient;
    }

    // -----------------------------------------------------------------------------------------------------------------

    private void merchantDeposit(MerchantUser merchantUser, Double amount) {
        Wallet wallet = walletService.getWalletByUserId(merchantUser.getId());
        wallet.setBalance(wallet.getBalance() + amount);
        walletService.createOrUpdateWallet(wallet);
    }

    private void sendNotification(Transaction transaction) {
        try {
            String message = String.format("Transação de R$ %.2f realizada com sucesso", 
                                          transaction.getAmount());
            NotificationRequest request = new NotificationRequest(message);
            notificationClient.notify(request);
        } catch (Exception e) {
            // System.err.println("Falha ao enviar notificação: " + e.getMessage());
        }
    }

    private TransactionStatus checkAuthorization() {
        try {
            AuthorizerResponse response = authorizerClient.authorize();
            if (response.getData().isAuthorization()) {
                return TransactionStatus.AUTHORIZED;
            }
            return TransactionStatus.FAILED;
        } catch (Exception e) {
            return TransactionStatus.FAILED;
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "transactions:user", key = "#dto.payerId"),
            @CacheEvict(value = "transactions:user", key = "#dto.payeeId")
    })
    public TransactionResponseDTO transfer(TransactionRequestDTO dto) {

        if (dto.getPayerId().equals(dto.getPayeeId())) {
            throw new SameUserTransactionException();
        }

        User payer = userRepository.findById(dto.getPayerId())
                .orElseThrow(() -> new UserNotFoundException(dto.getPayerId()));

        if (payer.getUserType() == UserType.MERCHANT) {
            throw new MerchantCannotTransferException(payer.getId());
        }

        User payee = userRepository.findById(dto.getPayeeId())
                .orElseThrow(() -> new UserNotFoundException(dto.getPayeeId()));

        // Cria a transação no banco.
        Transaction transaction = new Transaction();
        transaction.setAmount(dto.getAmount());
        transaction.setPayer(payer);
        transaction.setPayee(payee);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setStatus(TransactionStatus.PENDING);
        Transaction savedTransaction = transactionRepository.save(transaction);

        // API de autenticação

        TransactionStatus authStatus = checkAuthorization();
        savedTransaction.setStatus(authStatus);

        if (authStatus != TransactionStatus.AUTHORIZED) {
            savedTransaction.setStatus(authStatus);
            transactionRepository.save(savedTransaction);
            throw new TransactionNotAuthorizedException();
        }

        // Realiza a transação.
        walletService.withdraw(payer.getId(), dto.getAmount());

        if (payee instanceof MerchantUser) {
            merchantDeposit((MerchantUser) payee, dto.getAmount());
        } else {
            walletService.deposit(payee.getId(), dto.getAmount());
        }

        savedTransaction.setStatus(TransactionStatus.COMPLETED);
        Transaction completedTransaction = transactionRepository.save(savedTransaction);

        // API de notificação.

        sendNotification(completedTransaction);

        return new TransactionResponseDTO(completedTransaction);
    }

    // Sobrecarga para quando as APIs estiverem fora do ar.
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "transactions:user", key = "#dto.payerId"),
            @CacheEvict(value = "transactions:user", key = "#dto.payeeId")
    })
    public TransactionResponseDTO transfer(TransactionRequestDTO dto, TransactionStatus authStatus) {

        if (dto.getPayerId().equals(dto.getPayeeId())) {
            throw new SameUserTransactionException();
        }

        User payer = userRepository.findById(dto.getPayerId())
                .orElseThrow(() -> new UserNotFoundException(dto.getPayerId()));

        if (payer.getUserType() == UserType.MERCHANT) {
            throw new MerchantCannotTransferException(payer.getId());
        }

        User payee = userRepository.findById(dto.getPayeeId())
                .orElseThrow(() -> new UserNotFoundException(dto.getPayeeId()));

        // Cria a transação no banco.
        Transaction transaction = new Transaction();
        transaction.setAmount(dto.getAmount());
        transaction.setPayer(payer);
        transaction.setPayee(payee);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setStatus(TransactionStatus.PENDING);
        Transaction savedTransaction = transactionRepository.save(transaction);

        if (authStatus != TransactionStatus.AUTHORIZED) {
            savedTransaction.setStatus(authStatus);
            transactionRepository.save(savedTransaction);
            throw new TransactionNotAuthorizedException();
        }

        // Realiza a transação.
        walletService.withdraw(payer.getId(), dto.getAmount());

        if (payee instanceof MerchantUser) {
            merchantDeposit((MerchantUser) payee, dto.getAmount());
        } else {
            walletService.deposit(payee.getId(), dto.getAmount());
        }

        savedTransaction.setStatus(TransactionStatus.COMPLETED);
        Transaction completedTransaction = transactionRepository.save(savedTransaction);

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
