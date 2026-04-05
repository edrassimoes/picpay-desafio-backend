package br.com.edras.picpaysimplificado.fixtures;

import br.com.edras.picpaysimplificado.entity.CommonUser;
import br.com.edras.picpaysimplificado.entity.Transaction;
import br.com.edras.picpaysimplificado.entity.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransactionFixtures {

    public static Transaction createTransaction() {
        return createTransaction(1L, 2L);
    }

    public static Transaction createTransaction(Long payerId, Long payeeId) {
        CommonUser payer = CommonUserFixtures.createValidCommonUser(payerId);
        CommonUser payee = CommonUserFixtures.createValidCommonUser(payeeId);

        Transaction transaction = new Transaction();

        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setPayer(payer);
        transaction.setPayee(payee);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setStatus(TransactionStatus.AUTHORIZED);
        return transaction;
    }

}
