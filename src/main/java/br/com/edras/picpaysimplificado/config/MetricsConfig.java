package br.com.edras.picpaysimplificado.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter transfersSuccess(MeterRegistry registry) {
        return Counter.builder("transfers_total")
                .tag("status", "success")
                .description("Total de transferências concluídas com sucesso")
                .register(registry);
    }

    @Bean
    public Counter transfersFailed(MeterRegistry registry) {
        return Counter.builder("transfers_total")
                .tag("status", "failed")
                .description("Total de transferências negadas pelo autorizador")
                .register(registry);
    }

    @Bean
    public Counter transfersAborted(MeterRegistry registry) {
        return Counter.builder("transfers_total")
                .tag("status", "aborted")
                .description("Total de transferências abortadas por erro interno")
                .register(registry);
    }

    @Bean
    public Counter transfersDeniedInsufficientFunds(MeterRegistry registry) {
        return Counter.builder("transfer_denied_insufficient_funds_total")
                .description("Transferências negadas por saldo insuficiente")
                .register(registry);
    }

    @Bean
    public Counter transfersDeniedInvalidPayer(MeterRegistry registry) {
        return Counter.builder("transfer_denied_invalid_payer_total")
                .description("Tentativas de transferência por lojistas (bloqueadas)")
                .register(registry);
    }

    @Bean
    public Counter externalAuthorizerErrors(MeterRegistry registry) {
        return Counter.builder("external_service_errors_total")
                .tag("service", "authorizer")
                .description("Erros na chamada ao autorizador externo")
                .register(registry);
    }

    @Bean
    public Counter externalNotificationErrors(MeterRegistry registry) {
        return Counter.builder("external_service_errors_total")
                .tag("service", "notification")
                .description("Erros na chamada ao serviço de notificação")
                .register(registry);
    }

}
