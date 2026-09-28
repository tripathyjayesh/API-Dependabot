package org.apidependabot.repository;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Opens a local checkout or a temporary, read-only GitHub snapshot for analysis. */
@Service
public class GitHubRepositoryService {
    private static final Duration CLONE_TIMEOUT = Duration.ofMinutes(3);

    public RepositoryCheckout open(String repositoryReference) throws IOException, InterruptedException {
        repositoryReference = normalizeRepositoryReference(repositoryReference);
        if (repositoryReference == null || repositoryReference.isBlank()) {
            throw new IllegalArgumentException("Provide a repository path or GitHub HTTPS URL.");
        }
        if (!repositoryReference.regionMatches(true, 0, "https://", 0, 8)) {
            Path local = Path.of(repositoryReference).toAbsolutePath().normalize().toRealPath();
            if (!Files.isDirectory(local)) {
                throw new IllegalArgumentException("Repository path is not a directory: " + repositoryReference);
            }
            return new RepositoryCheckout(local, null);
        }

        GitHubReference reference = parseGitHubReference(repositoryReference);
        Path temporaryDirectory = Files.createTempDirectory("api-dependabot-github-");
        Path checkout = temporaryDirectory.resolve("repository");
        Path cloneLog = temporaryDirectory.resolve("git-output.log");
        try {
            List<String> command = new ArrayList<>(List.of("git", "clone", "--quiet", "--depth", "1"));
            if (reference.ref() != null) {
                command.addAll(List.of("--branch", reference.ref()));
            }
            command.add(reference.cloneUrl());
            command.add(checkout.toString());

            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(cloneLog.toFile());
            builder.environment().put("GIT_TERMINAL_PROMPT", "0");
            String token = System.getenv("GITHUB_TOKEN");
            if (token != null && !token.isBlank()) {
                String credentials = Base64.getEncoder().encodeToString(
                        ("x-access-token:" + token).getBytes(StandardCharsets.UTF_8));
                Map<String, String> environment = builder.environment();
                environment.put("GIT_CONFIG_COUNT", "1");
                environment.put("GIT_CONFIG_KEY_0", "http.extraheader");
                environment.put("GIT_CONFIG_VALUE_0", "AUTHORIZATION: basic " + credentials);
            }
            Process process = builder.start();
            if (!process.waitFor(CLONE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor();
                throw new IOException("GitHub checkout exceeded the three-minute timeout.");
            }
            if (process.exitValue() != 0) {
                String detail = Files.exists(cloneLog) ? Files.readString(cloneLog, StandardCharsets.UTF_8).strip() : "no git output";
                throw new IOException("GitHub checkout failed (git exit " + process.exitValue() + "): " + detail);
            }
            return new RepositoryCheckout(checkout, temporaryDirectory);
        } catch (IOException | InterruptedException | RuntimeException exception) {
            try {
                deleteTree(temporaryDirectory);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    public static GitHubReference parseGitHubReference(String value) {
        value = normalizeRepositoryReference(value);
        final URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("GitHub repository must be an HTTPS URL in the form https://github.com/owner/repo[#ref].", exception);
        }
        String rawPath = uri.getRawPath();
        String[] segments = rawPath == null ? new String[0] : rawPath.split("/");
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
                || !"github.com".equalsIgnoreCase(uri.getHost()) || uri.getPort() != -1
                || uri.getRawQuery() != null || segments.length != 3
                || segments[1].isBlank() || segments[2].isBlank()) {
            throw new IllegalArgumentException("GitHub repository must be an HTTPS URL in the form https://github.com/owner/repo[#ref].");
        }
        String owner = segments[1];
        String repository = segments[2].endsWith(".git")
                ? segments[2].substring(0, segments[2].length() - 4) : segments[2];
        if (!owner.matches("[A-Za-z0-9_.-]+") || !repository.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("GitHub owner and repository names must use standard URL-safe characters.");
        }
        String ref = uri.getFragment();
        if (ref != null && (ref.isBlank() || ref.startsWith("-") || ref.contains("\n") || ref.contains("\r"))) {
            throw new IllegalArgumentException("GitHub ref must be a non-empty branch, tag, or commit name.");
        }
        return new GitHubReference(owner, repository, ref,
                "https://github.com/" + owner + "/" + repository + ".git");
    }

    /** Accepts a pasted Markdown link when its label and target are the same URL. */
    public static String normalizeRepositoryReference(String value) {
        if (value == null || !value.startsWith("[https://")) {
            return value;
        }
        int separator = value.indexOf("](");
        if (separator < 0 || !value.endsWith(")")) {
            return value;
        }
        String label = value.substring(1, separator);
        String target = value.substring(separator + 2, value.length() - 1);
        return label.equals(target) ? target : value;
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                DosFileAttributeView dosAttributes = Files.getFileAttributeView(path, DosFileAttributeView.class);
                if (dosAttributes != null && dosAttributes.readAttributes().isReadOnly()) {
                    dosAttributes.setReadOnly(false);
                }
                Files.deleteIfExists(path);
            }
        }
    }

    public record GitHubReference(String owner, String repository, String ref, String cloneUrl) { }

    public static final class RepositoryCheckout implements AutoCloseable {
        private final Path path;
        private final Path temporaryDirectory;

        private RepositoryCheckout(Path path, Path temporaryDirectory) {
            this.path = path;
            this.temporaryDirectory = temporaryDirectory;
        }

        public Path path() {
            return path;
        }

        @Override
        public void close() throws IOException {
            deleteTree(temporaryDirectory);
        }
    }
}
