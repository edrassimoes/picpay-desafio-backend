package br.com.edras.picpaysimplificado.dto.transaction;

import br.com.edras.picpaysimplificado.entity.Transaction;
import br.com.edras.picpaysimplificado.entity.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransactionResponseDTO {

    private Long transactionId;
    private Long payerId;
    private String payerName;
    private Long payeeId;
    private String payeeName;
    private BigDecimal amount;
    private LocalDateTime timestamp;
    private TransactionStatus transactionStatus;

    public TransactionResponseDTO() {}

    public TransactionResponseDTO(Transaction transaction) {
        this.transactionId = transaction.getId();
        this.payerId = transaction.getPayer().getId();
        this.payerName = transaction.getPayer().getName();
        this.payeeId = transaction.getPayee().getId();
        this.payeeName = transaction.getPayee().getName();
        this.amount = transaction.getAmount();
        this.timestamp = transaction.getCreatedAt();
        this.transactionStatus = transaction.getStatus();
    }

    public TransactionResponseDTO(Long transactionId, Long payerId, String payerName, Long payeeId, String payeeName, BigDecimal amount, LocalDateTime timestamp, TransactionStatus transactionStatus) {
        this.transactionId = transactionId;
        this.payerId = payerId;
        this.payerName = payerName;
        this.payeeId = payeeId;
        this.payeeName = payeeName;
        this.amount = amount;
        this.timestamp = timestamp;
        this.transactionStatus = transactionStatus;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public Long getPayerId() {
        return payerId;
    }

    public void setPayerId(Long payerId) {
        this.payerId = payerId;
    }

    public String getPayerName() {
        return payerName;
    }

    public void setPayerName(String payerName) {
        this.payerName = payerName;
    }

    public Long getPayeeId() {
        return payeeId;
    }

    public void setPayeeId(Long payeeId) {
        this.payeeId = payeeId;
    }

    public String getPayeeName() {
        return payeeName;
    }

    public void setPayeeName(String payeeName) {
        this.payeeName = payeeName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public TransactionStatus getTransactionStatus() {
        return transactionStatus;
    }

    public void setTransactionStatus(TransactionStatus transactionStatus) {
        this.transactionStatus = transactionStatus;
    }

}
