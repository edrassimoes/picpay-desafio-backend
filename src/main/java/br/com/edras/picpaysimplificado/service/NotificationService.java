package br.com.edras.picpaysimplificado.service;

import br.com.edras.picpaysimplificado.integration.NotificationClient;
import br.com.edras.picpaysimplificado.integration.NotificationRequest;
import br.com.edras.picpaysimplificado.entity.Transaction;
import io.micrometer.core.instrument.Counter;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final NotificationClient notificationClient;
    private final Counter externalNotificationErrors;

    public NotificationService(NotificationClient notificationClient, Counter externalNotificationErrors) {
        this.notificationClient = notificationClient;
        this.externalNotificationErrors = externalNotificationErrors;
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
            externalNotificationErrors.increment();
        }
    }

}