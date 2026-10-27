package com.bank.transaction.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads backend/.env into the Spring environment for every launch path
 * (spring-boot:run, IDE runs, and @SpringBootTest), not only main().
 * Real environment variables and system properties still take precedence.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    // Keys from .env that application.yml placeholders depend on
    private static final List<String> KEYS = List.of(
            "SPRING_DATASOURCE_URL",
            "SPRING_DATASOURCE_USERNAME",
            "SPRING_DATASOURCE_PASSWORD",
            "APP_BASE_URL",
            "BREVO_SMTP_KEY",
            "BREVO_SMTP_LOGIN",
            "MAIL_FROM",
            "MAIL_PORT",
            "JWT_SECRET",
            "ADMIN_INITIAL_PASSWORD",
            "ADMIN_PASSWORD_RESET",
            "FRONTEND_URL",
            "CORS_ALLOWED_ORIGINS"
    );

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();

        Map<String, Object> values = new HashMap<>();
        for (String key : KEYS) {
            String value = dotenv.get(key);
            if (value != null) {
                values.put(key, value);
                // Useful for debugging local startup issues
                System.out.println("Loaded ENV Var: " + key + " = " + (isSensitive(key) ? "********" : value));
            } else {
                System.err.println("Warning: Environment variable " + key + " is missing from .env!");
            }
        }
        environment.getPropertySources().addLast(new MapPropertySource("dotenv", values));
    }

    private static boolean isSensitive(String key) {
        return key.contains("PASSWORD") || key.contains("KEY") || key.contains("SECRET");
    }
}
