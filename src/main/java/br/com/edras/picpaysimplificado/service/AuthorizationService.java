package br.com.edras.picpaysimplificado.service;

import br.com.edras.picpaysimplificado.integration.AuthorizerClient;
import br.com.edras.picpaysimplificado.integration.AuthorizerResponse;
import br.com.edras.picpaysimplificado.entity.enums.TransactionStatus;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {

    private final AuthorizerClient authorizerClient;
    private final Counter externalAuthorizerErrors;

    public AuthorizationService(AuthorizerClient authorizerClient, Counter externalAuthorizerErrors) {
        this.authorizerClient = authorizerClient;
        this.externalAuthorizerErrors = externalAuthorizerErrors;
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
        externalAuthorizerErrors.increment();
        return TransactionStatus.PENDING;
    }

}