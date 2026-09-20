package example.tenantkeys;

import java.nio.file.Path;

public final class TenantCredentialCommand {
    private TenantCredentialCommand() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0) usage();
        InfraiConfig config = InfraiConfig.load(Path.of("config/application.properties"), System.getenv());
        TenantCredentialService service = new TenantCredentialService(new InfraiClient(config));

        switch (args[0]) {
            case "provision" -> {
                if (args.length != 4) usage();
                ProvisionedTenant result = service.provision(args[1], args[2], args[3]);
                System.out.printf("Provisioned %s user=%s key=%s secret=%s%n",
                        result.tenant(), result.userId(), result.keyId(), result.secret());
            }
            case "offboard" -> {
                if (args.length != 4) usage();
                OffboardedTenant result = service.offboard(args[1], args[2], args[3]);
                System.out.println("Offboarded " + result.tenant());
            }
            default -> usage();
        }
    }

    private static void usage() {
        throw new IllegalArgumentException(
                "Usage: provision <tenant> <email> <name> | offboard <tenant> <user-id> <key-id>");
    }
}
