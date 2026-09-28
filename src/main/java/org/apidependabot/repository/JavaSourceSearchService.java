package org.apidependabot.repository;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class JavaSourceSearchService {
    private static final Set<String> IGNORED_DIRECTORIES = Set.of(".git", "target", "build", ".idea");

    public List<SearchHit> search(Path repository, List<String> requestedTerms) throws IOException {
        Path root = repository.toRealPath();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Repository path is not a directory: " + repository);
        }
        List<String> terms = requestedTerms.stream()
                .map(String::trim)
                .filter(term -> !term.isEmpty())
                .distinct()
                .toList();
        if (terms.isEmpty()) {
            throw new IllegalArgumentException("Provide at least one non-empty Java search term.");
        }

        List<SearchHit> hits = new ArrayList<>();
        List<Path> javaFiles = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                if (!directory.equals(root) && IGNORED_DIRECTORIES.contains(directory.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (attributes.isRegularFile() && file.getFileName().toString().endsWith(".java")) {
                    javaFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        javaFiles.sort(Comparator.comparing(Path::toString));
        for (Path file : javaFiles) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String sourceLine = lines.get(i).strip();
                for (String term : terms) {
                    if (matches(sourceLine, term)) {
                        String relative = root.relativize(file).toString().replace('\\', '/');
                        hits.add(new SearchHit(term, relative, i + 1, sourceLine, classify(sourceLine, term)));
                    }
                }
            }
        }
        return List.copyOf(hits);
    }

    public String renderMarkdown(List<SearchHit> hits) {
        if (hits.isEmpty()) {
            return "### Java repository impact search\n\nNo matches found.\n";
        }
        StringBuilder report = new StringBuilder("### Java repository impact search\n\n");
        for (SearchHit hit : hits) {
            report.append("- `").append(hit.file()).append(':').append(hit.line()).append("` ")
                    .append("matched `").append(hit.term()).append("` [").append(hit.evidenceType()).append("]: `")
                    .append(hit.sourceLine().replace("`", "\\`")).append("`\n");
        }
        return report.toString();
    }

    private boolean matches(String line, String term) {
        if (!term.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            return line.contains(term);
        }
        return Pattern.compile("(?<![A-Za-z0-9_$])" + Pattern.quote(term) + "(?![A-Za-z0-9_$])")
                .matcher(line).find();
    }

    private String classify(String line, String term) {
        String escaped = Pattern.quote(term);
        boolean methodDeclaration = line.matches(".*\\b(?:public|protected|private|static|final|abstract|synchronized|default|native|\\s)+"
                + "[\\w.$<>?,\\[\\]]+\\s+" + escaped + "\\s*\\(.*");
        if (methodDeclaration) {
            return "declaration";
        }
        return line.contains("\"") || line.contains("'") ? "literal or reference" : "reference";
    }

    public record SearchHit(String term, String file, int line, String sourceLine, String evidenceType) { }
}
