package com.osc.store.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Loads `.env` from the process, the jar folder, or DOTENV_PATH so deploys resolve ${...} placeholders. */
public class DotEnvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("spring.datasource.url", "SPRING_DATASOURCE_URL"),
            Map.entry("spring.datasource.username", "SPRING_DATASOURCE_USERNAME"),
            Map.entry("spring.datasource.password", "SPRING_DATASOURCE_PASSWORD"),
            Map.entry("spring.mail.host", "SPRING_MAIL_HOST"),
            Map.entry("spring.mail.username", "SPRING_MAIL_USERNAME"),
            Map.entry("spring.mail.password", "SPRING_MAIL_PASSWORD"),
            Map.entry("spring.mail.from", "SPRING_MAIL_FROM"),
            Map.entry("minio.endpoint", "MINIO_ENDPOINT"),
            Map.entry("minio.access-key", "MINIO_ACCESS_KEY"),
            Map.entry("minio.secret-key", "MINIO_SECRET_KEY"),
            Map.entry("minio.bucket", "MINIO_BUCKET"),
            Map.entry("minio.public-url", "MINIO_PUBLIC_URL")
    );

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path file = findEnvFile();
        if (file == null) return;
        Map<String, Object> values = parse(file);
        if (values.isEmpty()) return;
        environment.getPropertySources().addLast(new MapPropertySource("dotenv", values));
    }

    private Path findEnvFile() {
        String explicit = System.getenv("DOTENV_PATH");
        if (explicit != null && !explicit.isBlank()) {
            Path path = Path.of(explicit).toAbsolutePath();
            if (Files.isRegularFile(path)) return path;
            Path nested = path.resolve(".env");
            if (Files.isRegularFile(nested)) return nested;
        }
        for (Path dir : searchDirs()) {
            if (dir == null) continue;
            Path candidate = dir.resolve(".env");
            if (Files.isRegularFile(candidate)) return candidate;
            Path nested = dir.resolve("backend").resolve(".env");
            if (Files.isRegularFile(nested)) return nested;
        }
        return null;
    }

    private Iterable<Path> searchDirs() {
        LinkedHashMap<String, Path> dirs = new LinkedHashMap<>();
        addDir(dirs, Path.of("").toAbsolutePath());
        addDir(dirs, Path.of(System.getProperty("user.dir", ".")).toAbsolutePath());
        addDir(dirs, jarDirectory());
        Path walk = Path.of("").toAbsolutePath();
        for (int i = 0; i < 5 && walk != null; i++) {
            addDir(dirs, walk);
            walk = walk.getParent();
        }
        return dirs.values();
    }

    private void addDir(Map<String, Path> dirs, Path dir) {
        if (dir == null) return;
        dirs.putIfAbsent(dir.normalize().toString(), dir);
    }

    private Path jarDirectory() {
        try {
            URI uri = DotEnvEnvironmentPostProcessor.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path path = Path.of(uri);
            if (Files.isRegularFile(path)) path = path.getParent();
            return path;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> parse(Path file) {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("export ")) line = line.substring(7).trim();
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).trim();
                if (key.isEmpty() || System.getenv(key) != null) continue;
                String value = stripQuotes(line.substring(eq + 1).trim());
                if (!out.containsKey(key)) out.put(key, value);
                String alias = ALIASES.get(key);
                if (alias != null && System.getenv(alias) == null) {
                    Object existing = out.get(alias);
                    if (existing == null || ("SPRING_MAIL_FROM".equals(alias) && !looksLikeEmail(String.valueOf(existing)) && looksLikeEmail(value))) {
                        out.put(alias, value);
                    }
                }
            }
        } catch (IOException ignored) {
            // Startup continues; missing or unreadable .env just means placeholders stay unresolved.
        }
        return out;
    }

    private boolean looksLikeEmail(String value) {
        return value != null && value.contains("@") && value.indexOf('@') > 0 && value.indexOf('@') < value.length() - 1;
    }

    private String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            if ((first == '"' || first == '\'') && value.charAt(value.length() - 1) == first) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
