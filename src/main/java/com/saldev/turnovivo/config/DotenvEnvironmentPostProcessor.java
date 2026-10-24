package com.saldev.turnovivo.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/*
 * Loads local variables from a ".env" file in the project root
 * (if it exists) and exposes them as a low-priority property source.
 *
 * Goal: to allow running the application locally ("dev" profile) without
 * having to manually export environment variables in every terminal or
 * relying exclusively on IDE run configurations.
 *
 * Actual operating system environment variables ALWAYS take
 * precedence over values ​​defined in ".env," as this source is
 * added to the end of the property resolution chain.
 *
 * The ".env" file must never be committed to the repository (see .gitignore).
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "dotenvFileProperties";
    private static final String DOTENV_FILE_NAME = ".env";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path dotenvPath = locateDotenvFile();

        if (dotenvPath == null) {
            return;
        }

        Properties properties = parseDotenvFile(dotenvPath);

        if (!properties.isEmpty()) {
            environment.getPropertySources()
                    .addLast(new PropertiesPropertySource(PROPERTY_SOURCE_NAME, properties));
        }
    }

    /*
     * Searches for the ".env" file in a way that is resilient to the current working directory.
     *
     * Strategy: It begins from several anchor points (the current working directory
     * and the location of the compiled code/JAR file itself) and traverses upward
     * through parent directories toward the file system root, looking for a ".env" file.
     * This allows the application to start up regardless of whether it is launched
     * from an IDE, via Maven, or using "java -jar" from any arbitrary path.
     */
    private Path locateDotenvFile() {
        for (Path anchor : candidateAnchorDirectories()) {
            Path current = anchor;
            while (current != null) {
                Path candidate = current.resolve(DOTENV_FILE_NAME);
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
                current = current.getParent();
            }
        }
        return null;
    }

    private List<Path> candidateAnchorDirectories() {
        Set<Path> anchors = new LinkedHashSet<>();

        // 1) Current working directory.
        anchors.add(Path.of("").toAbsolutePath().normalize());

        // 2) "user.dir" property (may differ from the actual CWD depending on the launcher).
        String userDir = System.getProperty("user.dir");
        if (userDir != null && !userDir.isBlank()) {
            anchors.add(Path.of(userDir).toAbsolutePath().normalize());
        }

        // 3) Location of the compiled code or running JAR.
        Path codeLocation = codeSourceLocation();
        if (codeLocation != null) {
            Path start = Files.isDirectory(codeLocation) ? codeLocation : codeLocation.getParent();
            if (start != null) {
                anchors.add(start.toAbsolutePath().normalize());
            }
        }

        return new ArrayList<>(anchors);
    }

    private Path codeSourceLocation() {
        try {
            CodeSource codeSource = getClass().getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                return null;
            }
            URL location = codeSource.getLocation();

            if ("file".equalsIgnoreCase(location.getProtocol())) {
                URI uri = location.toURI();
                return Path.of(uri);
            }

            // Spring Boot executable JAR: classes are loaded using schemes
            // "jar:file:/.../app.jar!/..." or "nested:/.../app.jar/!BOOT-INF/classes/".
            // We extract the path of the external JAR so we can navigate up to the ".env" file.
            String raw = location.toString();
            int jarIndex = raw.indexOf(".jar");
            if (jarIndex < 0) {
                return null;
            }
            String jarPath = raw.substring(0, jarIndex + ".jar".length())
                    .replaceFirst("^jar:", "")
                    .replaceFirst("^nested:", "")
                    .replaceFirst("^file:", "");
            return Path.of(jarPath);
        } catch (URISyntaxException | RuntimeException ex) {
            return null;
        }
    }

    private Properties parseDotenvFile(Path dotenvPath) {
        Properties properties = new Properties();

        List<String> lines;
        try {
            lines = Files.readAllLines(dotenvPath);
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo leer el archivo .env", ex);
        }

        for (String rawLine : lines) {
            String line = rawLine.strip();

            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            // Support both "export KEY=VALUE" and the simple "KEY=VALUE" format.
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).strip();
            }

            int separatorIndex = line.indexOf('=');
            if (separatorIndex <= 0) {
                continue;
            }

            String key = line.substring(0, separatorIndex).strip();
            String value = line.substring(separatorIndex + 1).strip();
            value = stripSurroundingQuotes(value);

            properties.setProperty(key, value);
        }

        return properties;
    }

    private String stripSurroundingQuotes(String value) {
        boolean wrappedInDoubleQuotes = value.length() >= 2
                && value.startsWith("\"") && value.endsWith("\"");
        boolean wrappedInSingleQuotes = value.length() >= 2
                && value.startsWith("'") && value.endsWith("'");

        if (wrappedInDoubleQuotes || wrappedInSingleQuotes) {
            return value.substring(1, value.length() - 1);
        }

        return value;
    }

    @Override
    public int getOrder() {
        // Must be executed before Spring Boot processes application.yml,
        // so that the ${DEMO_ADMIN_EMAIL:} placeholders can be resolved.
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
