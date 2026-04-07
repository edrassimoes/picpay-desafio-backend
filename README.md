![banner](/assets/Banner.png)

![Build](https://img.shields.io/github/actions/workflow/status/edrassimoes/picpay-simplificado/workflow.yml) ![Coverage](https://img.shields.io/badge/coverage-85%25-brightgreen) ![Java](https://img.shields.io/badge/Java-21-007396) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F) ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791) ![Redis](https://img.shields.io/badge/Redis-Cache-DC382D) ![Tests](https://img.shields.io/badge/tests-JUnit%20%7C%20Mockito-25A162) ![Mutation](https://img.shields.io/badge/mutation-tested-6A1B9A) ![Observability](https://img.shields.io/badge/observability-Prometheus%20%7C%20Grafana-F46800) ![Docker](https://img.shields.io/badge/docker-ready-2496ED) ![License](https://img.shields.io/badge/license-MIT-E91E63)

## 📌 Sobre o projeto

O [desafio técnico do PicPay](https://github.com/PicPay/picpay-desafio-backend) propõe a criação de uma plataforma simplificada de pagamentos. O objetivo central é permitir que usuários realizem transferências entre si, respeitando regras de negócio específicas para dois perfis distintos: usuários comuns e lojistas.


---

## 📋 Índice

- [Funcionalidades](#funcionalidades)
- [Arquitetura](#arquitetura)
- [Decisões técnicas](#decisões-técnicas)
- [Modelo de dados](#modelo-de-dados)
- [Endpoints da API](#endpoints-da-api)
- [Segurança](#segurança)
- [Idempotência](#idempotência)
- [Integração com serviços externos](#integração-com-serviços-externos)
- [Observabilidade](#observabilidade)
- [Qualidade e testes](#qualidade-e-testes)
- [Como executar](#como-executar)
- [Stack de tecnologias](#stack-de-tecnologias)

---

## ⚙️ Funcionalidades

### Usuários
- Cadastro de usuários comuns (CPF) e lojistas (CNPJ)
- Validação de CPF e CNPJ via anotações customizadas (`@CPF`, `@CNPJ`)
- Unicidade de e-mail e documento garantida em nível de banco de dados
- Senhas armazenadas com hash com BCrypt
- CRUD completo: criação, listagem, busca por ID, atualização e exclusão

### Carteiras
- Cada usuário possui uma carteira criada automaticamente no cadastro
- Operações de depósito e saque com validações de saldo e tipo de usuário

### Transferências
- Usuários comuns podem transferir para qualquer outro usuário
- Lojistas só recebem transferências — não podem enviá-las
- Verificação de saldo antes de executar qualquer transferência
- Bloqueio de auto-transferência (payer ≠ payee)
- Em caso de falha, rollback automático via `@Transactional`
- Idempotência por chave no header `Idempotency-Key`

### Autenticação
- Login com e-mail e senha
- Retorno de token JWT assinado com par de chaves RSA
- Token com expiração de 1 hora

---

## 🧱 Arquitetura

O projeto segue uma **arquitetura em camadas** (Layered Architecture), com responsabilidades claras e bem definidas:

```
┌─────────────────────────────────────────────┐
│              Controller Layer               │  ← Recebe requisições HTTP, valida entrada
├─────────────────────────────────────────────┤
│               Service Layer                 │  ← Regras de negócio e orquestração
├─────────────────────────────────────────────┤
│             Repository Layer                │  ← Acesso ao banco de dados via JPA
├─────────────────────────────────────────────┤
│          Database / Cache / External        │  ← PostgreSQL, Redis, APIs externas
└─────────────────────────────────────────────┘
```

### Fluxo completo de uma transferência

```
Cliente
  │
  ▼
JWT Filter ──── token inválido ──► 401 Unauthorized
  │
  ▼
TransactionController
  │
  ▼
IdempotencyService
  ├── Redis hit (COMPLETED) ──────────────────► retorna resposta cacheada
  ├── Redis hit (PROCESSING) ─────────────────► 409 Conflict
  └── Cache miss ──► cria chave no PostgreSQL ─► salva no Redis
  │
  ▼
TransactionService
  ├── payer == payee ────────────────────────► 400 Bad Request
  ├── payer é lojista ────────────────────────► 403 Forbidden
  ├── saldo insuficiente ─────────────────────► 400 Bad Request
  │
  ▼
AuthorizationService (Feign + CircuitBreaker)
  ├── autorizado ─────────────────────────────► continua
  ├── não autorizado ──────────────────────────► 403 Forbidden
  └── serviço offline ────────────────────────► PENDING (fallback)
  │
  ▼
WalletService
  ├── withdraw (payer)
  └── deposit (payee) ou merchantDeposit (receber pagamentos)
  │
  ▼
transactionRepository.save(COMPLETED)
  │
  ▼
ApplicationEventPublisher ──► TransactionCompletedEvent
  │                                    │
  │                                    ▼
  │                          NotificationListener
  │                                    │
  │                                    ▼
  │                          NotificationService (Feign)
  │
  ▼
IdempotencyService.save(status=COMPLETED, response=JSON)
  ├── PostgreSQL
  └── Redis (TTL 24h)
  │
  ▼
201 Created
```

### Herança de entidades

A hierarquia de usuários usa **Single Table Inheritance** (uma única tabela `tb_users` com uma coluna discriminadora `user_type`):

```
User (abstract)
  ├── CommonUser  → documento: CPF
  └── MerchantUser → documento: CNPJ
```

Essa abordagem simplifica as queries e mantém integridade referencial centralizada. A diferenciação de comportamento entre os tipos é feita via `instanceof` e polimorfismo.

---

## 🔧 Decisões técnicas

### Por que Single Table Inheritance?
Com apenas dois subtipos e poucos campos exclusivos (CPF e CNPJ), a estratégia `SINGLE_TABLE` evita joins desnecessários e mantém o acesso aos dados simples e eficiente.

### Por que Redis antes do banco na idempotência?
A chave de idempotência é consultada em praticamente toda requisição de transferência. Manter essa verificação no Redis (em memória) evita uma leitura ao banco em cada chamada, reduzindo latência no caminho crítico da aplicação.

### Por que Resilience4j com Circuit Breaker no autorizador?
Como proposto pelo desafio, o autorizador externo é um ponto de falha fora do nosso controle. Sem circuit breaker, uma lentidão ou indisponibilidade desse serviço bloquearia todas as transferências indefinidamente. Com o fallback, a transação recebe status `PENDING` em vez de falhar, permitindo reprocessamento futuro.

### Por que eventos para notificações (`ApplicationEventPublisher`)?
Desacopla o fluxo de transferência do serviço de notificação. Se a notificação falhar, a transação já foi concluída e o evento pode ser reprocessado. O uso de `@TransactionalEventListener` garante que a notificação só seja disparada após o commit da transação ser confirmado.

---

## 🗂️ Modelo de dados

### `tb_users`
| Coluna | Tipo | Descrição |
|--------|------|-----------|
| `id` | BIGSERIAL PK | Identificador único |
| `user_type` | VARCHAR(20) | `COMMON` ou `MERCHANT` |
| `name` | VARCHAR(255) | Nome completo |
| `email` | VARCHAR(255) UNIQUE | E-mail de login |
| `password` | VARCHAR(255) | Hash BCrypt |
| `cpf` | VARCHAR(14) | Apenas para usuários comuns |
| `cnpj` | VARCHAR(18) | Apenas para lojistas |

Constraint de banco garante que `COMMON` sempre tenha CPF e `MERCHANT` sempre tenha CNPJ — sem depender da validação da aplicação.

### `tb_wallets`
| Coluna | Tipo | Descrição |
|--------|------|-----------|
| `id` | BIGSERIAL PK | Identificador único |
| `balance` | DOUBLE PRECISION | Saldo atual |
| `user_id` | BIGINT FK UNIQUE | Relação 1:1 com `tb_users` |

### `tb_transactions`
| Coluna | Tipo | Descrição |
|--------|------|-----------|
| `id` | BIGSERIAL PK | Identificador único |
| `amount` | DOUBLE PRECISION | Valor transferido |
| `payer_id` | BIGINT FK | Usuário pagador |
| `payee_id` | BIGINT FK | Usuário recebedor |
| `created_at` | TIMESTAMP | Data/hora da transação |
| `status` | VARCHAR(30) | `PENDING`, `AUTHORIZED`, `COMPLETED`, `FAILED` |

Constraint de banco garante `payer_id <> payee_id` em nível de SQL.

### `idempotency_keys`
| Coluna | Tipo | Descrição |
|--------|------|-----------|
| `key` | VARCHAR(255) PK | Chave única enviada pelo cliente |
| `status` | VARCHAR(50) | `PROCESSING` ou `COMPLETED` |
| `response` | TEXT | JSON serializado da resposta |
| `created_at` | TIMESTAMP | Data/hora de criação |

---

## 📍 Endpoints da API

A documentação interativa completa está disponível via Swagger em `http://localhost:8080/swagger-ui/index.html`.

### Autenticação

| Método | Endpoint | Auth | Descrição |
|--------|----------|------|-----------|
| `POST` | `/auth/login` | Pública | Autentica usuário e retorna JWT |

**Request:**
```json
{
  "email": "joao@email.com",
  "password": "senha123"
}
```

**Response `200 OK`:**
```json
{
  "token": "eyJhbGciOiJSUzI1NiJ9..."
}
```

---

### Usuários

| Método | Endpoint | Auth | Descrição |
|--------|----------|------|-----------|
| `POST` | `/users` | Pública | Cria novo usuário |
| `GET` | `/users` | JWT | Lista todos os usuários |
| `GET` | `/users/{id}` | JWT | Busca usuário por ID |
| `PUT` | `/users/{id}` | JWT | Atualiza dados do usuário |
| `DELETE` | `/users/{id}` | JWT | Remove usuário |

**Request — criar usuário comum:**
```json
{
  "name": "João Silva",
  "email": "joao@email.com",
  "password": "senha123",
  "document": "529.982.247-25",
  "userType": "COMMON"
}
```

**Request — criar lojista:**
```json
{
  "name": "Loja do João",
  "email": "loja@email.com",
  "password": "senha123",
  "document": "11.222.333/0001-81",
  "userType": "MERCHANT"
}
```

---

### Carteiras

| Método | Endpoint | Auth | Descrição |
|--------|----------|------|-----------|
| `GET` | `/wallets/user/{userId}` | JWT | Consulta carteira do usuário |
| `PATCH` | `/wallets/user/{userId}/deposit` | JWT | Deposita valor na carteira |
| `PATCH` | `/wallets/user/{userId}/withdraw` | JWT | Saca valor da carteira |

---

### Transações

| Método | Endpoint | Auth | Descrição |
|--------|----------|------|-----------|
| `POST` | `/transactions` | JWT | Realiza transferência |
| `GET` | `/transactions/{id}` | JWT | Busca transação por ID |
| `GET` | `/transactions/users/{id}` | JWT | Lista transações de um usuário |

**Request — transferência:**

> Requer o header `Idempotency-Key` com um UUID único por operação.

```
POST /transactions
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
Authorization: Bearer {token}
```
```json
{
  "amount": 150.00,
  "payerId": 1,
  "payeeId": 2
}
```

**Response `201 Created`:**
```json
{
  "transactionId": 42,
  "payerId": 1,
  "payerName": "João Silva",
  "payeeId": 2,
  "payeeName": "Maria Souza",
  "amount": 150.00,
  "createdAt": "2025-11-01T14:30:00",
  "status": "COMPLETED"
}
```

---

## 🔐 Segurança

A autenticação é implementada com **Spring Security + OAuth2 Resource Server**, usando um par de chaves **RSA** para assinar e validar os tokens JWT.

**Fluxo:**
1. Cliente envia e-mail e senha para `POST /auth/login`
2. Spring Security valida as credenciais via `AuthenticationManager`
3. `JwtService` gera um token JWT assinado com a chave privada RSA (expiração: 1h)
4. Todos os requests subsequentes devem incluir o token no header `Authorization: Bearer {token}`
5. O Spring valida o token com a chave pública RSA a cada requisição

**Rotas públicas** (não requerem token):
- `POST /auth/login`
- `POST /users`
- `/swagger-ui/**` e `/v3/api-docs/**`
- `/actuator/prometheus`

---

## 🔁 Idempotência

Idempotência garante que o mesmo request executado múltiplas vezes produza o mesmo resultado sem efeitos colaterais. Em transferências financeiras, isso é crítico para evitar cobranças duplicadas em caso de retry do cliente.

**Como funciona:**

Cada request de transferência deve incluir um header `Idempotency-Key` com um UUID único gerado pelo cliente. O servidor armazena a chave e o resultado da operação.

**Estratégia Redis-first:**

```
Request com Idempotency-Key
        │
        ▼
   Consulta Redis
        │
   ┌────┴────┐
  Hit       Miss
   │          │
   ▼          ▼
Retorna    Consulta banco
imediato       │
           ┌───┴───┐
         Existe  Não existe
           │          │
           ▼          ▼
       Popula    Cria com status
       Redis     PROCESSING → salva
       retorna   no Redis → retorna
```

**Cenários tratados:**

| Situação | Comportamento |
|----------|---------------|
| Primeiro request | Chave criada com status `PROCESSING` |
| Request idêntico enquanto processa | `409 Conflict` |
| Request idêntico após conclusão | Retorna resposta original (sem reprocessar) |
| Dois requests simultâneos | Um cria, o outro recebe `DataIntegrityViolationException` e busca no banco |

O TTL no Redis é de **24 horas**.

---

## 🌐 Integração com serviços externos

Ambas as integrações usam **Spring Cloud OpenFeign**, que permite definir clientes HTTP declarativamente através de interfaces Java.

### Autorizador de transações

- **URL:** `https://util.devi.tools/api/v2/authorize`
- **Método:** `GET`
- Consultado antes de toda transferência
- Retorna autorização booleana
- Protegido por **Circuit Breaker (Resilience4j)**:
  - Se o serviço estiver indisponível, o fallback retorna `TransactionStatus.PENDING`
  - Evita que falhas externas bloqueiem o sistema inteiro

### Serviço de notificações

- **URL:** `https://util.devi.tools/api/v1/notify`
- **Método:** `POST`
- Disparado via evento após o commit da transação (`@TransactionalEventListener`)
- Desacoplado do fluxo principal — falha na notificação não reverte a transferência

---

## 📊 Observabilidade

### Métricas

O Spring Boot Actuator expõe métricas em `/actuator/prometheus`, coletadas pelo **Prometheus** e visualizadas no **Grafana** (porta `3000`).

Métricas disponíveis incluem:
- Uso de CPU e memória da JVM
- Número de requisições HTTP por endpoint e status
- Latência das requisições
- Conexões ativas no pool do banco de dados
- Estado do circuit breaker do autorizador

### Endpoints de saúde

| Endpoint | Descrição |
|----------|-----------|
| `/actuator/health` | Status da aplicação e dependências |
| `/actuator/info` | Informações da aplicação |
| `/actuator/prometheus` | Métricas no formato Prometheus |

---

## 🧪 Qualidade e testes

A suíte de testes cobre os três principais tipos de validação:

### Testes unitários — `*Test.java`

Usam **JUnit 5** + **Mockito** para testar as classes de serviço em isolamento, com todas as dependências mockadas.

Cobrem cenários de sucesso e todos os cenários de exceção, por exemplo:
- `TransactionServiceTest`: transferência bem-sucedida, payer lojista, auto-transferência, autorizador negando, resposta idempotente cacheada, conflito de idempotência
- `IdempotencyServiceTest`: cache hit, cache miss, concorrência com fallback ao banco, sincronização de cache

### Testes de integração — `*IntegrationTest.java`

Usam **Testcontainers** para subir uma instância real do **PostgreSQL** em Docker durante os testes. Validam o comportamento da aplicação de ponta a ponta, desde a requisição HTTP até o banco de dados.

### Testes de controller — `*ControllerTest.java`

Usam **MockMvc** para testar os endpoints HTTP de forma isolada, verificando status codes, headers e corpo da resposta para cada cenário.

### Cobertura — JaCoCo

O JaCoCo gera um relatório HTML de cobertura por classe e método. Executar `mvn verify` gera o relatório em `target/site/jacoco/index.html`, que também é servido em `http://localhost:8080/jacoco` quando a aplicação está rodando.

### Mutation Testing — Pitest (PIT)

O Pitest vai além da cobertura de linhas: ele introduz mutações propositais no código (troca `>` por `>=`, inverte condicionais, remove chamadas a métodos) e verifica se os testes existentes detectam essas mudanças. Isso valida a **efetividade** dos testes, não apenas sua existência.

Executar: `mvn test-compile org.pitest:pitest-maven:mutationCoverage`

---

## 🚀 Como executar

### Pré-requisitos
- Docker e Docker Compose instalados

### Passos

**1. Clone o repositório**
```bash
git clone https://github.com/edrassimoes/picpay-simplificado.git
cd picpay-simplificado
```

**2. Suba os containers**
```bash
docker compose up --build
```

Isso provisiona automaticamente:
- Aplicação Spring Boot na porta `8080`
- PostgreSQL 16 na porta `5432`
- Redis na porta `6379`
- Prometheus na porta `9090`
- Grafana na porta `3000`

**3. Aguarde a inicialização**

O Flyway executa as migrations automaticamente ao subir. A aplicação estará pronta quando o log exibir `Started PicpaySimplificadoApplication`.

### Acessos

| Serviço | URL | Descrição |
|---------|-----|-----------|
| Swagger UI | http://localhost:8080/swagger-ui/index.html | Documentação e testes da API |
| Grafana | http://localhost:3000 | Dashboards de métricas |
| Prometheus | http://localhost:9090 | Coleta de métricas |
| JaCoCo | http://localhost:8080/jacoco | Relatório de cobertura de testes |

---

## 🛠️ Stack de tecnologias

### Core
| Tecnologia | Versão | Uso |
|------------|--------|-----|
| Java | 21 | Linguagem principal |
| Spring Boot | 3.4.0 | Framework base |
| Spring Data JPA | — | Persistência e acesso ao banco |
| Spring Security | — | Autenticação e autorização |
| Spring OAuth2 Resource Server | — | Validação de tokens JWT |
| Spring Cloud OpenFeign | 2024.0.0 | Clientes HTTP declarativos |
| Spring Data Redis | — | Cache e idempotência |

### Banco de dados
| Tecnologia | Versão | Uso |
|------------|--------|-----|
| PostgreSQL | 16 | Banco de dados relacional |
| Flyway | — | Versionamento de schema |
| Redis | latest | Cache de idempotência (TTL 24h) |

### Segurança
| Tecnologia | Versão | Uso |
|------------|--------|-----|
| JJWT | 0.12.6 | Geração e parsing de tokens JWT |
| Nimbus JOSE | — | Codificação RSA para JWT |
| BCrypt | — | Hash de senhas |

### Resiliência
| Tecnologia | Versão | Uso |
|------------|--------|-----|
| Resilience4j | — | Circuit Breaker no autorizador |

### Observabilidade
| Tecnologia | Versão | Uso |
|------------|--------|-----|
| Spring Boot Actuator | — | Exposição de métricas e saúde |
| Micrometer | — | Bridge entre Actuator e Prometheus |
| Prometheus | latest | Coleta e armazenamento de métricas |
| Grafana | latest | Visualização e dashboards |
| SpringDoc OpenAPI | 2.7.0 | Geração do Swagger UI |

### Testes
| Tecnologia | Versão | Uso |
|------------|--------|-----|
| JUnit 5 | — | Framework de testes |
| Mockito | — | Mocks para testes unitários |
| Spring Boot Test + MockMvc | — | Testes de controller |
| Testcontainers | 1.21.4 | PostgreSQL real em testes de integração |
| JaCoCo | 0.8.14 | Cobertura de testes |
| Pitest | 1.22.1 | Mutation testing |

### Infraestrutura
| Tecnologia | Versão | Uso |
|------------|--------|-----|
| Docker | — | Containerização |
| Docker Compose | — | Orquestração local dos serviços |
