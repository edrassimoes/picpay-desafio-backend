package br.com.edras.picpaysimplificado.service;

import br.com.edras.picpaysimplificado.entity.CommonUser;
import br.com.edras.picpaysimplificado.entity.MerchantUser;
import br.com.edras.picpaysimplificado.entity.User;
import br.com.edras.picpaysimplificado.entity.Wallet;
import br.com.edras.picpaysimplificado.entity.enums.UserType;
import br.com.edras.picpaysimplificado.dto.user.UserRequestDTO;
import br.com.edras.picpaysimplificado.dto.user.UserResponseDTO;
import br.com.edras.picpaysimplificado.dto.user.UserUpdateDTO;
import br.com.edras.picpaysimplificado.exception.user.*;
import br.com.edras.picpaysimplificado.repository.CommonUserRepository;
import br.com.edras.picpaysimplificado.repository.MerchantUserRepository;
import br.com.edras.picpaysimplificado.repository.TransactionRepository;
import br.com.edras.picpaysimplificado.repository.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final MerchantUserRepository merchantUserRepository;
    private final CommonUserRepository commonUserRepository;
    private final TransactionRepository transactionRepository;
    private final WalletService walletService;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, MerchantUserRepository merchantUserRepository, CommonUserRepository commonUserRepository, TransactionRepository transactionRepository, WalletService walletService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.merchantUserRepository = merchantUserRepository;
        this.commonUserRepository = commonUserRepository;
        this.transactionRepository = transactionRepository;
        this.walletService = walletService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponseDTO createUser(UserRequestDTO dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
             throw new EmailAlreadyExistsException(dto.getEmail());
        }

        User user;
        String hashedPassword = passwordEncoder.encode(dto.getPassword());

        if (dto.getUserType() == UserType.COMMON) {

            if (dto.getCpf() == null || dto.getCpf().isBlank()) {
                throw new IllegalArgumentException("CPF é obrigatório para usuários comuns");
            }

            if (commonUserRepository.existsByCpf(dto.getCpf())) {
             throw new DocumentAlreadyExistsException(dto.getCpf());
            }

            if (dto.getCnpj() != null && !dto.getCnpj().isBlank()) {
                throw new InvalidDocumentTypeException("CNPJ não é permitido a usuários comuns");
            }

            user = new CommonUser(dto.getName(), dto.getEmail(), hashedPassword, dto.getCpf());

        } else {

            if (dto.getCnpj() == null || dto.getCnpj().isBlank()) {
                throw new IllegalArgumentException("CNPJ é obrigatório para usuários lojistas");
            }

            if (merchantUserRepository.existsByCnpj(dto.getCnpj())) {
                 throw new DocumentAlreadyExistsException(dto.getCnpj());
            }

            if (dto.getCpf() != null && !dto.getCpf().isBlank()) {
                throw new InvalidDocumentTypeException("CPF não é permitido a usuários lojistas");
            }

            user = new MerchantUser(dto.getName(), dto.getEmail(), hashedPassword, dto.getCnpj());
        }

        User savedUser = userRepository.save(user);

        Wallet wallet = new Wallet();
        wallet.setUser(savedUser);
        wallet.setBalance(BigDecimal.ZERO);
        walletService.createOrUpdateWallet(wallet);

        return new UserResponseDTO(savedUser);
    }

    @Cacheable("users:list")
    public List<UserResponseDTO> findAllUsers() {
        return userRepository.findAll().stream()
                .map(UserResponseDTO::new)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "users", key = "#id")
    public UserResponseDTO findUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        return new UserResponseDTO(user);
    }

    @CacheEvict(value = "users", key = "#id")
    @Transactional
    public UserResponseDTO updateUser(Long id, UserUpdateDTO dto) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        if (dto.getName() != null && !dto.getName().isBlank()) {
            existingUser.setName(dto.getName());
        }

        if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
            if (!existingUser.getEmail().equals(dto.getEmail()) &&
                 userRepository.existsByEmail(dto.getEmail())) {
                 throw new EmailAlreadyExistsException(dto.getEmail());
            }
            existingUser.setEmail(dto.getEmail());
        }

        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            String hashedPassword = passwordEncoder.encode(dto.getPassword());
            existingUser.setPassword(hashedPassword);
        }

        User updatedUser = userRepository.save(existingUser);
        return new UserResponseDTO(updatedUser);
    }

    @CacheEvict(value = "users", key = "#id")
    @Transactional
    public void deleteUserById(Long id) {
        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException(id);
        }

        if (transactionRepository.existsByPayerId(id)
                || transactionRepository.existsByPayeeId(id)) {

            throw new UserHasTransactionsException();
        }

        userRepository.deleteById(id);
    }

}
