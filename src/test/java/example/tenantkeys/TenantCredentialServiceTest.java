package example.tenantkeys;

import java.util.ArrayList;
import java.util.List;

public final class TenantCredentialServiceTest {
    public static void main(String[] args) {
        provisionsCommerceBoundary();
        removesUserWhenKeyCreationIsRejected();
        System.out.println("TenantCredentialServiceTest passed");
    }

    private static void provisionsCommerceBoundary() {
        RecordingGateway gateway = new RecordingGateway(false);
        ProvisionedTenant result = new TenantCredentialService(gateway)
                .provision("tenant-river", "ops@river.example", "River Operations");

        check(result.tenant().equals("tenant-river"), "tenant must be retained");
        check(gateway.scopes.equals(List.of("checkout", "fulfillment", "receipts", "customer_order_updates")),
                "key must be limited to the four commerce scopes");
        check(gateway.createdIdempotencyKeys.size() == 2, "both creates need idempotency keys");
        check(!gateway.createdIdempotencyKeys.get(0).equals(gateway.createdIdempotencyKeys.get(1)),
                "user and key writes need distinct idempotency keys");
    }

    private static void removesUserWhenKeyCreationIsRejected() {
        RecordingGateway gateway = new RecordingGateway(true);
        try {
            new TenantCredentialService(gateway)
                    .provision("tenant-river", "ops@river.example", "River Operations");
            throw new AssertionError("provision should return the key rejection");
        } catch (InfraiException expected) {
            check(gateway.deletedUsers.equals(List.of("user-17")),
                    "a user without its tenant key must be removed");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class RecordingGateway implements TenantCredentialGateway {
        private final boolean rejectKey;
        private final List<String> createdIdempotencyKeys = new ArrayList<>();
        private final List<String> deletedUsers = new ArrayList<>();
        private List<String> scopes;

        private RecordingGateway(boolean rejectKey) { this.rejectKey = rejectKey; }

        @Override
        public UserRef createUser(String email, String name, String tenant, String idempotencyKey) {
            createdIdempotencyKeys.add(idempotencyKey);
            return new UserRef("user-17");
        }

        @Override
        public KeyRef createKey(String tenant, List<String> scopes, String idempotencyKey) {
            createdIdempotencyKeys.add(idempotencyKey);
            this.scopes = scopes;
            if (rejectKey) throw new InfraiException("POLICY_REJECTION", "Key request rejected", 400);
            return new KeyRef("key-31", "one-time-secret");
        }

        @Override public void deleteUser(String userId) { deletedUsers.add(userId); }
        @Override public void revokeKey(String keyId) {}
    }
}
