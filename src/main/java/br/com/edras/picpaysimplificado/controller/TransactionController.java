package br.com.edras.picpaysimplificado.controller;

import br.com.edras.picpaysimplificado.dto.transaction.TransactionRequestDTO;
import br.com.edras.picpaysimplificado.dto.transaction.TransactionResponseDTO;
import br.com.edras.picpaysimplificado.service.TransactionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Transactions")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    @Operation(summary = "Create a new transaction (transfer between users)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Transaction created successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized (missing or invalid authentication token)"),
            @ApiResponse(responseCode = "400", description = "Invalid transaction (same user or not authorized)"),
            @ApiResponse(responseCode = "403", description = "Merchant users cannot transfer money"),
            @ApiResponse(responseCode = "404", description = "Payer or payee not found")
    })
    public ResponseEntity<TransactionResponseDTO> createTransaction(
            @Parameter(description = "Unique idempotency key to prevent duplicate transactions", required = true)
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody TransactionRequestDTO dto){
        TransactionResponseDTO newTransaction = transactionService.transfer(key, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(newTransaction);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Find transaction by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction found successfully"),
            @ApiResponse(responseCode = "404", description = "Transaction not found")
    })
    public ResponseEntity<TransactionResponseDTO> findById(@PathVariable Long id) {
        TransactionResponseDTO transaction = transactionService.findById(id);
        return ResponseEntity.ok().body(transaction);
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Find all transactions by user ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transactions retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<List<TransactionResponseDTO>> findTransactionsByUserId(@PathVariable Long id) {
        List<TransactionResponseDTO> transactions = transactionService.findTransactionsByUserId(id);
        return ResponseEntity.ok().body(transactions);
    }

}
