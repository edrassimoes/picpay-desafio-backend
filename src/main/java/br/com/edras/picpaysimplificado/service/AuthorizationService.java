package br.com.edras.picpaysimplificado.service;

import br.com.edras.picpaysimplificado.client.AuthorizerClient;
import br.com.edras.picpaysimplificado.client.AuthorizerResponse;
import br.com.edras.picpaysimplificado.domain.enums.TransactionStatus;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {

    private final AuthorizerClient authorizerClient;

    public AuthorizationService(AuthorizerClient authorizerClient) {
        this.authorizerClient = authorizerClient;
    }

    @CircuitBreaker(name = "authorizerService", fallbackMethod = "authorizeFallback")
    public TransactionStatus authorize() {

        AuthorizerResponse response = authorizerClient.authorize();

        if (response.getData().isAuthorization()) {
            return TransactionStatus.AUTHORIZED;
        }

        return TransactionStatus.FAILED;
    }

    public TransactionStatus authorizeFallback(Throwable t) {
        return TransactionStatus.PENDING;
    }

}