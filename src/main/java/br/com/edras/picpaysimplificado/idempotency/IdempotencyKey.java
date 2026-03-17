package br.com.edras.picpaysimplificado.idempotency;

import br.com.edras.picpaysimplificado.idempotency.enums.RequestStatus;
import jakarta.persistence.*;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey implements Serializable {

    @Id
    private String key;

    @Enumerated(EnumType.STRING)
    private RequestStatus status;

    private String response;

    private LocalDateTime createdAt;

    public IdempotencyKey() {}

    public IdempotencyKey(String key, RequestStatus status, String response, LocalDateTime createdAt) {
        this.key = key;
        this.status = status;
        this.response = response;
        this.createdAt = createdAt;
    }

    public String getKey() {
        return key;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

}