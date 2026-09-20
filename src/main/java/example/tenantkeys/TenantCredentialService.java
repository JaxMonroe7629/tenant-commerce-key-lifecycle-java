package example.tenantkeys;

import java.util.List;
import java.util.UUID;

public final class TenantCredentialService {
    static final List<String> COMMERCE_SCOPES = List.of(
            "checkout", "fulfillment", "receipts", "customer_order_updates");

    private final TenantCredentialGateway gateway;

    public TenantCredentialService(TenantCredentialGateway gateway) {
        this.gateway = gateway;
    }

    public ProvisionedTenant provision(String tenant, String email, String name) {
        String operationId = UUID.randomUUID().toString();
        UserRef user = gateway.createUser(email, name, tenant, operationId + ":user");
        try {
            KeyRef key = gateway.createKey(tenant, COMMERCE_SCOPES, operationId + ":key");
            return new ProvisionedTenant(tenant, user.id(), key.id(), key.secret());
        } catch (RuntimeException rejected) {
            try {
                gateway.deleteUser(user.id());
            } catch (RuntimeException cleanup) {
                rejected.addSuppressed(cleanup);
            }
            throw rejected;
        }
    }

    public OffboardedTenant offboard(String tenant, String userId, String keyId) {
        gateway.revokeKey(keyId);
        gateway.deleteUser(userId);
        return new OffboardedTenant(tenant);
    }
}

interface TenantCredentialGateway {
    UserRef createUser(String email, String name, String tenant, String idempotencyKey);
    KeyRef createKey(String tenant, List<String> scopes, String idempotencyKey);
    void deleteUser(String userId);
    void revokeKey(String keyId);
}

record ProvisionedTenant(String tenant, String userId, String keyId, String secret) {}
record OffboardedTenant(String tenant) {}
