package example.tenantkeys;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class InfraiClient implements TenantCredentialGateway {
    private final InfraiConfig config;
    private final HttpClient http;

    public InfraiClient(InfraiConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    InfraiClient(InfraiConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    @Override
    public UserRef createUser(String email, String name, String tenant, String idempotencyKey) {
        Map<String, Object> data = call("POST", "/v1/auth/user/create", Map.of(
                "email", email,
                "name", name,
                "metadata", Map.of("tenant", tenant),
                "idempotency_key", idempotencyKey));
        return new UserRef(requiredString(data, "id"));
    }

    @Override
    public KeyRef createKey(String tenant, List<String> scopes, String idempotencyKey) {
        Map<String, Object> data = call("POST", "/v1/account/keys/create", Map.of(
                "name", "commerce-" + tenant,
                "scopes", scopes,
                "idempotency_key", idempotencyKey));
        return new KeyRef(requiredString(data, "id"), requiredString(data, "key"));
    }

    @Override
    public void deleteUser(String userId) {
        call("DELETE", "/v1/auth/user/delete/" + segment(userId), null);
    }

    @Override
    public void revokeKey(String keyId) {
        call("DELETE", "/v1/account/keys/revoke/" + segment(keyId), null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String method, String path, Map<String, Object> body) {
        for (int attempt = 0; ; attempt++) {
            HttpResponse<String> response = send(method, path, body);
            Object decoded = Json.decode(response.body());
            if (!(decoded instanceof Map<?, ?> rawEnvelope)) {
                throw new InfraiException("INVALID_ENVELOPE", "Response was not a JSON object", response.statusCode());
            }
            Map<String, Object> envelope = (Map<String, Object>) rawEnvelope;
            if (response.statusCode() == 429 && attempt < config.maxRetries()) {
                pause(response, attempt);
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                Map<String, Object> error = envelope.get("error") instanceof Map<?, ?> value
                        ? (Map<String, Object>) value : Map.of();
                throw new InfraiException(String.valueOf(error.getOrDefault("code", "INFRAI_REJECTED")),
                        String.valueOf(error.getOrDefault("message", "Request rejected")), response.statusCode());
            }
            if (response.statusCode() >= 500) {
                throw new InfraiException("TRANSPORT_RESPONSE", "Upstream transport response", response.statusCode());
            }
            Object data = envelope.get("data");
            return data instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        }
    }

    private HttpResponse<String> send(String method, String path, Map<String, Object> body) {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(Json.encode(body));
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.baseUrl() + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .method(method, publisher)
                .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            throw new InfraiException("TRANSPORT_IO", exception.getMessage(), 0);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new InfraiException("TRANSPORT_INTERRUPTED", "Request interrupted", 0);
        }
    }

    private static void pause(HttpResponse<?> response, int attempt) {
        long milliseconds = response.headers().firstValue("Retry-After")
                .map(InfraiClient::retryAfterMillis)
                .orElse(250L * (1L << attempt));
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new InfraiException("RETRY_INTERRUPTED", "Retry interrupted", 429);
        }
    }

    private static long retryAfterMillis(String value) {
        try { return Math.max(0, Long.parseLong(value) * 1000L); }
        catch (NumberFormatException ignored) { return 1000L; }
    }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String requiredString(Map<String, Object> data, String field) {
        Object value = data.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new InfraiException("INVALID_ENVELOPE", "Missing response field: " + field, 0);
        }
        return text;
    }
}

record UserRef(String id) {}
record KeyRef(String id, String secret) {}

final class InfraiException extends RuntimeException {
    private final String code;
    private final int status;

    InfraiException(String code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    String code() { return code; }
    int status() { return status; }
}
