package org.apidependabot.answer;

import org.apidependabot.retrieval.LexicalRetrievalService;
import org.apidependabot.repository.JavaSourceSearchService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Per-request, read-only tools scoped to one local consumer repository. */
public class ReActRepositoryTools {
    private static final int MAX_TOOL_CALLS = 8;
    private static final int MAX_SEARCH_HITS = 20;
    private static final int MAX_READ_LINES = 80;
    private static final int MAX_OUTPUT_CHARS = 12_000;

    private final Path repositoryRoot;
    private final String contractDiff;
    private final JavaSourceSearchService sourceSearchService;
    private final LexicalRetrievalService retrievalService;
    private final List<ToolObservation> observations = new ArrayList<>();
    private int callCount;

    public ReActRepositoryTools(Path repositoryRoot, String contractDiff,
                                JavaSourceSearchService sourceSearchService,
                                LexicalRetrievalService retrievalService) throws IOException {
        this.repositoryRoot = repositoryRoot.toRealPath();
        this.contractDiff = contractDiff == null ? "" : contractDiff;
        this.sourceSearchService = sourceSearchService;
        this.retrievalService = retrievalService;
        if (!Files.isDirectory(this.repositoryRoot)) {
            throw new IllegalArgumentException("Repository path is not a directory: " + repositoryRoot);
        }
    }

    @Tool(name = "inspectOpenApiDiff", description = "Read the deterministic OpenAPI contract diff. Use this to confirm what changed in the API before assessing consumer impact.")
    public String inspectOpenApiDiff() {
        return record("inspectOpenApiDiff", contractDiff.isBlank() ? "No OpenAPI diff was supplied." : contractDiff);
    }

    @Tool(name = "searchJava", description = "Search Java source and tests in the supplied consumer repository for one symbol or phrase. Returns bounded matching lines with paths and line numbers. Use this to locate likely impact sites.")
    public String searchJava(@ToolParam(description = "One Java identifier or short phrase to search for") String term) {
        if (term == null || term.isBlank() || term.length() > 100) {
            return record("searchJava", "Search term must be 1 to 100 characters.");
        }
        try {
            List<JavaSourceSearchService.SearchHit> hits = sourceSearchService.search(repositoryRoot, List.of(term));
            StringBuilder output = new StringBuilder();
            for (JavaSourceSearchService.SearchHit hit : hits.stream().limit(MAX_SEARCH_HITS).toList()) {
                output.append(hit.file()).append(':').append(hit.line()).append(" [")
                        .append(hit.evidenceType()).append("]: ").append(hit.sourceLine()).append('\n');
            }
            if (output.isEmpty()) {
                output.append("No Java matches found for '").append(term).append("'.");
            } else if (hits.size() > MAX_SEARCH_HITS) {
                output.append("(showing first ").append(MAX_SEARCH_HITS).append(" of ").append(hits.size()).append(" matches)");
            }
            return record("searchJava", output.toString());
        } catch (IOException | RuntimeException exception) {
            return record("searchJava", "Search failed: " + safeMessage(exception));
        }
    }

    @Tool(name = "readJavaFile", description = "Read up to 80 consecutive lines from a Java file inside the consumer repository. Provide a repository-relative path and optionally a starting line (defaults to line 1). Paths must be repository-relative. Use after searchJava to inspect surrounding code.")
    public String readJavaFile(
            @ToolParam(description = "Repository-relative path to a .java file") String relativePath,
            @ToolParam(description = "Optional first line number, starting at 1; defaults to 1") Integer requestedStartLine) {
        if (relativePath == null || relativePath.isBlank()) {
            return record("readJavaFile", "Provide a repository-relative Java file path.");
        }
        if (!relativePath.replace('\\', '/').endsWith(".java")) {
            return record("readJavaFile", "Only Java files inside the repository can be read.");
        }
        int startLine = requestedStartLine == null || requestedStartLine < 1 ? 1 : requestedStartLine;
        try {
            Path candidate = repositoryRoot.resolve(relativePath).normalize();
            if (!candidate.startsWith(repositoryRoot)) {
                return record("readJavaFile", "Rejected path outside the repository.");
            }
            Path realFile = candidate.toRealPath();
            if (!realFile.startsWith(repositoryRoot) || !Files.isRegularFile(realFile)
                    || !realFile.getFileName().toString().endsWith(".java")) {
                return record("readJavaFile", "Only Java files inside the repository can be read.");
            }
            List<String> lines = Files.readAllLines(realFile, StandardCharsets.UTF_8);
            if (startLine > lines.size()) {
                return record("readJavaFile", "Start line is past the end of the file (" + lines.size() + " lines).");
            }
            int actualEnd = Math.min(startLine + MAX_READ_LINES - 1, lines.size());
            StringBuilder output = new StringBuilder();
            for (int line = startLine; line <= actualEnd; line++) {
                output.append(line).append(": ").append(lines.get(line - 1)).append('\n');
            }
            return record("readJavaFile", relativePath.replace('\\', '/') + ":" + startLine + "-" + actualEnd + "\n" + output);
        } catch (IOException | RuntimeException exception) {
            return record("readJavaFile", "Could not read file: " + safeMessage(exception));
        }
    }

    @Tool(name = "retrieveEvidence", description = "Rank relevant source, test, build and diff chunks for a question. Returns up to five chunks with source line ranges.")
    public String retrieveEvidence(
            @ToolParam(description = "Question about the API migration or consumer code") String question,
            @ToolParam(description = "Number of evidence chunks to return, from 1 to 5") int topK) {
        if (question == null || question.isBlank() || question.length() > 500 || topK < 1 || topK > 5) {
            return record("retrieveEvidence", "Provide a question up to 500 characters and topK from 1 to 5.");
        }
        try {
            var chunks = retrievalService.retrieve(repositoryRoot, question, contractDiff, topK);
            return record("retrieveEvidence", retrievalService.renderMarkdown(chunks));
        } catch (IOException | RuntimeException exception) {
            return record("retrieveEvidence", "Retrieval failed: " + safeMessage(exception));
        }
    }

    public synchronized List<ToolObservation> observations() {
        return List.copyOf(observations);
    }

    private synchronized String record(String toolName, String rawOutput) {
        if (++callCount > MAX_TOOL_CALLS) {
            return "[tool budget exhausted: maximum " + MAX_TOOL_CALLS + " calls per answer]";
        }
        String output = rawOutput.length() > MAX_OUTPUT_CHARS
                ? rawOutput.substring(0, MAX_OUTPUT_CHARS) + "\n[output truncated]"
                : rawOutput;
        String id = "T" + callCount;
        observations.add(new ToolObservation(id, toolName, output));
        return "[" + id + "] " + output;
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName() : message;
    }

    public record ToolObservation(String id, String toolName, String output) { }
}
