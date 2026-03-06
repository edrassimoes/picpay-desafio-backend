package br.com.edras.picpaysimplificado.service;

import br.com.edras.picpaysimplificado.client.NotificationClient;
import br.com.edras.picpaysimplificado.client.NotificationRequest;
import br.com.edras.picpaysimplificado.domain.Transaction;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final NotificationClient notificationClient;

    public NotificationService(NotificationClient notificationClient) {
        this.notificationClient = notificationClient;
    }

    public void sendTransactionNotification(Transaction transaction) {
        try {

            String message = String.format(
                    "Transação de R$ %.2f realizada com sucesso",
                    transaction.getAmount()
            );

            NotificationRequest request = new NotificationRequest(message);
            notificationClient.notify(request);

        } catch (Exception e) {
            // log futuramente
        }
    }

}