package example.tenantkeys;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

public record InfraiConfig(String baseUrl, String apiKey, int maxRetries) {
    public static InfraiConfig load(Path propertyFile, Map<String, String> environment) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(propertyFile)) {
            properties.load(input);
        }
        String baseUrl = environment.getOrDefault("INFRAI_BASE_URL", required(properties, "infrai.base-url"));
        String apiKey = environment.get("INFRAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("INFRAI_API_KEY is required");
        }
        int retries = Integer.parseInt(environment.getOrDefault(
                "INFRAI_MAX_RETRIES", properties.getProperty("infrai.max-retries", "3")));
        return new InfraiConfig(stripTrailingSlash(baseUrl), apiKey, retries);
    }

    private static String required(Properties properties, String name) {
        String value = properties.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
