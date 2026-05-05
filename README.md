# PicPay Simplificado

![Build](https://img.shields.io/github/actions/workflow/status/edrassimoes/picpay-simplificado/workflow.yml) ![Coverage](https://img.shields.io/badge/coverage-85%25-brightgreen) ![Java](https://img.shields.io/badge/Java-21-007396) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F) ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791) ![Redis](https://img.shields.io/badge/Redis-Cache-DC382D) ![Tests](https://img.shields.io/badge/tests-JUnit%20%7C%20Mockito-25A162) ![Mutation](https://img.shields.io/badge/mutation-tested-6A1B9A) ![Observability](https://img.shields.io/badge/observability-Prometheus%20%7C%20Grafana-F46800) ![Docker](https://img.shields.io/badge/docker-ready-2496ED) ![License](https://img.shields.io/badge/license-MIT-E91E63)

Implementação do [desafio técnico do PicPay](https://github.com/PicPay/picpay-desafio-backend) focado em transferências financeiras entre usuários e lojistas.

## Tecnologias
* **Linguagem:** Java 21.
* **Framework:** Spring Boot 3.4.
* **Persistência:** PostgreSQL 16 & Flyway.
* **Cache & Idempotência:** Redis.
* **Segurança:** Spring Security, JWT (RSA) & BCrypt.
* **Resiliência:** Resilience4j (Circuit Breaker) & Feign.
* **Observabilidade:** Prometheus & Grafana.
* **Testes:** JUnit 5, Mockito, Testcontainers & Pitest (Mutação).

## Arquitetura
O sistema utiliza uma arquitetura em camadas com responsabilidades bem definidas.

<div align="center">
  <img src="assets/architecture-simple.svg" width="45%" />
</div>

<div align="center">
  <img src="assets/uml-entities.svg" width="45%" />
</div>


## Fluxo de Transferência
O processo inclui validação de perfil (lojistas apenas recebem), verificação de saldo e garantia de execução única via chave de idempotência.

<div align="center">
  <img src="assets/transaction-flow-final.svg" width="45%" />
</div>

## Endpoints Principais
A documentação interativa completa (Swagger) fica disponível em `/swagger-ui/index.html`.

<div align="center">
  <img src="assets/open-api.png" width="45%" />
</div>

## Observabilidade
Monitoramento em tempo real via Grafana e métricas exportadas pelo Actuator.

<div align="center">
  <img src="assets/grafana.png" width="45%" />
</div>

## Como Executar
Requer **Docker** e **Docker Compose**.

```bash
# Clone o repositório
git clone [https://github.com/edrassimoes/picpay-simplificado.git](https://github.com/edrassimoes/picpay-simplificado.git)

# Suba o ambiente completo
docker compose up --build
```
A aplicação estará disponível em http://localhost:8080.
