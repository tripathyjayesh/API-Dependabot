package org.apidependabot.retrieval;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LexicalRetrievalService {
    private static final int CHUNK_LINES = 25;
    private static final int OVERLAP_LINES = 5;
    private static final double BM25_K1 = 1.5;
    private static final double BM25_B = 0.75;
    private static final Set<String> IGNORED_DIRECTORIES = Set.of(".git", "target", "build", ".idea");
    private static final Set<String> STOP_WORDS = Set.of("a", "an", "and", "are", "as", "at", "be", "by", "does",
            "api", "consumer", "for", "from", "how", "in", "is", "it", "java", "of", "on", "or", "read",
            "the", "this", "to", "used", "uses", "what", "where", "which", "with");
    private static final Pattern WORDS = Pattern.compile("[a-z0-9_$]+");
    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");

    public List<RetrievedChunk> retrieve(Path repository, String question, String contractEvidence, int limit) throws IOException {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Provide a non-empty retrieval question.");
        }
        if (limit < 1 || limit > 20) {
            throw new IllegalArgumentException("Retrieval limit must be between 1 and 20.");
        }
        Path root = repository.toRealPath();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Repository path is not a directory: " + repository);
        }

        List<DocumentChunk> documents = indexRepository(root);
        if (contractEvidence != null && !contractEvidence.isBlank()) {
            documents.add(new DocumentChunk("openapi-diff.md", 1,
                    Math.max(1, contractEvidence.split("\\R", -1).length), "contract-change", contractEvidence));
        }
        List<String> queryTerms = tokenize(question).stream().filter(term -> !STOP_WORDS.contains(term)).distinct().toList();
        if (queryTerms.isEmpty()) {
            return List.of();
        }

        List<Map<String, Integer>> frequencies = documents.stream().map(document -> termFrequencies(document.content())).toList();
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (Map<String, Integer> frequency : frequencies) {
            frequency.keySet().forEach(term -> documentFrequency.merge(term, 1, Integer::sum));
        }
        double averageLength = frequencies.stream().mapToInt(LexicalRetrievalService::length).average().orElse(1.0);
        List<RetrievedChunk> ranked = new ArrayList<>();
        for (int index = 0; index < documents.size(); index++) {
            DocumentChunk document = documents.get(index);
            Map<String, Integer> frequency = frequencies.get(index);
            double score = score(queryTerms, frequency, documentFrequency, documents.size(), averageLength)
                    * kindWeight(document.kind());
            if (score > 0.0) {
                ranked.add(new RetrievedChunk(document.source(), document.startLine(), document.endLine(), document.kind(),
                        document.content(), score));
            }
        }
        return ranked.stream().sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed()
                        .thenComparing(RetrievedChunk::source).thenComparingInt(RetrievedChunk::startLine))
                .limit(limit).toList();
    }

    public String renderMarkdown(List<RetrievedChunk> chunks) {
        if (chunks.isEmpty()) {
            return "### Lexical retrieval evidence\n\nNo matching evidence found.\n";
        }
        StringBuilder report = new StringBuilder("### Lexical retrieval evidence\n\n");
        for (int index = 0; index < chunks.size(); index++) {
            RetrievedChunk chunk = chunks.get(index);
            report.append(index + 1).append(". **").append(chunk.kind()).append("** — `")
                    .append(chunk.source()).append(':').append(chunk.startLine()).append('-').append(chunk.endLine())
                    .append("` (score ").append(String.format(Locale.ROOT, "%.3f", chunk.score())).append(")\n\n")
                    .append("```text\n").append(chunk.content().strip()).append("\n```\n\n");
        }
        return report.toString();
    }

    private List<DocumentChunk> indexRepository(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
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
                if (attributes.isRegularFile() && isIndexable(file.getFileName().toString())) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        files.sort(Comparator.comparing(Path::toString));
        List<DocumentChunk> documents = new ArrayList<>();
        for (Path file : files) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String source = root.relativize(file).toString().replace('\\', '/');
            for (int start = 0; start < lines.size(); start += CHUNK_LINES - OVERLAP_LINES) {
                int end = Math.min(lines.size(), start + CHUNK_LINES);
                String content = String.join("\n", lines.subList(start, end));
                String kind = file.getFileName().toString().endsWith(".java")
                        ? source.contains("/test/") ? "java-test" : "java-source"
                        : file.getFileName().toString().equalsIgnoreCase("pom.xml") ? "maven-config" : "documentation";
                documents.add(new DocumentChunk(source, start + 1, end, kind, content));
                if (end == lines.size()) {
                    break;
                }
            }
        }
        return documents;
    }

    private boolean isIndexable(String name) {
        return name.endsWith(".java") || name.equalsIgnoreCase("README.md") || name.equalsIgnoreCase("pom.xml");
    }

    private double score(List<String> query, Map<String, Integer> frequencies, Map<String, Integer> documentFrequency,
                         int documentCount, double averageLength) {
        double score = 0.0;
        int documentLength = length(frequencies);
        for (String term : query) {
            int termFrequency = frequencies.getOrDefault(term, 0);
            if (termFrequency == 0) {
                continue;
            }
            int matchingDocuments = documentFrequency.getOrDefault(term, 0);
            double inverseDocumentFrequency = Math.log(1.0 + (documentCount - matchingDocuments + 0.5) / (matchingDocuments + 0.5));
            double denominator = termFrequency + BM25_K1 * (1.0 - BM25_B + BM25_B * documentLength / averageLength);
            score += inverseDocumentFrequency * termFrequency * (BM25_K1 + 1.0) / denominator;
        }
        return score;
    }

    private double kindWeight(String kind) {
        return switch (kind) {
            case "java-source" -> 1.2;
            case "java-test" -> 1.05;
            case "contract-change" -> 0.9;
            case "documentation" -> 0.7;
            case "maven-config" -> 0.25;
            default -> 1.0;
        };
    }

    private Map<String, Integer> termFrequencies(String text) {
        String camelSplit = CAMEL_BOUNDARY.matcher(text).replaceAll(" ");
        Matcher matcher = WORDS.matcher(camelSplit.toLowerCase(Locale.ROOT));
        Map<String, Integer> frequencies = new HashMap<>();
        while (matcher.find()) {
            frequencies.merge(matcher.group(), 1, Integer::sum);
        }
        return frequencies;
    }

    private List<String> tokenize(String text) {
        return new ArrayList<>(termFrequencies(text).keySet());
    }

    private static int length(Map<String, Integer> frequencies) {
        return frequencies.values().stream().mapToInt(Integer::intValue).sum();
    }

    private record DocumentChunk(String source, int startLine, int endLine, String kind, String content) { }

    public record RetrievedChunk(String source, int startLine, int endLine, String kind, String content, double score) { }
}
