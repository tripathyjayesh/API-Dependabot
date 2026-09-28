package org.apidependabot.diff;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apidependabot.answer.ReActAnswerService;
import org.apidependabot.answer.VanillaRagAnswerService;
import org.apidependabot.repository.JavaSourceSearchService;
import org.apidependabot.repository.GitHubRepositoryService;
import org.apidependabot.retrieval.LexicalRetrievalService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.io.FileOutputStream;
import java.io.FileDescriptor;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DiffCommandRunner implements ApplicationRunner {
    private static final PrintStream JSON_STDOUT = new PrintStream(
            new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
    private final OpenApiDiffService diffService;
    private final JavaSourceSearchService sourceSearchService;
    private final LexicalRetrievalService retrievalService;
    private final VanillaRagAnswerService answerService;
    private final ReActAnswerService reActAnswerService;
    private final GitHubRepositoryService repositoryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DiffCommandRunner(OpenApiDiffService diffService, JavaSourceSearchService sourceSearchService,
                             LexicalRetrievalService retrievalService, VanillaRagAnswerService answerService,
                             ReActAnswerService reActAnswerService, GitHubRepositoryService repositoryService) {
        this.diffService = diffService;
        this.sourceSearchService = sourceSearchService;
        this.retrievalService = retrievalService;
        this.answerService = answerService;
        this.reActAnswerService = reActAnswerService;
        this.repositoryService = repositoryService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        boolean json = "json".equalsIgnoreCase(optionalSingleValue(args, "format", "markdown"));
        Map<String, Object> result = baseResult("help");
        try {
            execute(args, json, result);
        } catch (Exception exception) {
            if (!json) {
                throw exception;
            }
            result.put("status", "error");
            result.put("errorType", exception.getClass().getSimpleName());
            result.put("error", safeMessage(exception));
        }
        if (json) {
            JSON_STDOUT.println(objectMapper.writeValueAsString(result));
            JSON_STDOUT.flush();
        }
    }

    private void execute(ApplicationArguments args, boolean json, Map<String, Object> result) throws Exception {
        String format = optionalSingleValue(args, "format", "markdown");
        if (!format.equalsIgnoreCase("markdown") && !format.equalsIgnoreCase("json")) {
            throw new IllegalArgumentException("--format must be either markdown or json.");
        }

        boolean hasOld = args.containsOption("old");
        boolean hasNew = args.containsOption("new");
        boolean hasRepository = args.containsOption("repo");
        boolean hasQuestion = args.containsOption("question");
        if (hasOld != hasNew) {
            throw new IllegalArgumentException("Provide both --old and --new OpenAPI files.");
        }
        if (args.containsOption("answer") && args.containsOption("react")) {
            throw new IllegalArgumentException("Use either --answer (Vanilla RAG) or --react, not both.");
        }
        if ((args.containsOption("answer") || args.containsOption("react") || args.containsOption("top-k"))
                && !hasQuestion) {
            throw new IllegalArgumentException("--answer, --react, and --top-k require --question=<migration question>.");
        }

        String diffReport = "";
        if (hasOld) {
            String oldSpec = Path.of(requiredSingleValue(args, "old")).toAbsolutePath().toString();
            String newSpec = Path.of(requiredSingleValue(args, "new")).toAbsolutePath().toString();
            diffReport = diffService.compareAsMarkdown(oldSpec, newSpec);
            result.put("oldSpec", oldSpec);
            result.put("newSpec", newSpec);
            if (!json) {
                System.out.println(diffReport);
            }
        }
        if (!diffReport.isBlank()) {
            result.put("contractDiff", diffReport);
            result.put("mode", "diff");
        }

        if (hasRepository) {
            String repositoryReference = GitHubRepositoryService.normalizeRepositoryReference(
                    requiredSingleValue(args, "repo"));
            try (GitHubRepositoryService.RepositoryCheckout checkout = repositoryService.open(repositoryReference)) {
            Path repository = checkout.path();
            result.put("repository", repositoryReference.regionMatches(true, 0, "https://", 0, 8)
                    ? repositoryReference : repository.toString());
            if (hasQuestion) {
                String question = requiredSingleValue(args, "question");
                result.put("question", question);
                if (args.containsOption("react")) {
                    result.put("mode", "react");
                    var answer = reActAnswerService.answer(repository, question, diffReport);
                    result.put("answer", answer.text());
                    result.put("toolTrace", answer.observations());
                    result.put("toolCallCount", answer.observations().size());
                    result.put("latencyMillis", answer.latencyMillis());
                    if (!json) {
                        System.out.println("### ReAct answer\n\n" + answer.text() + "\n");
                        System.out.println("Tool trace:");
                        if (answer.observations().isEmpty()) {
                            System.out.println("- The model did not call a repository tool.");
                        } else {
                            answer.observations().forEach(observation -> System.out.println("- [" + observation.id() + "] "
                                    + observation.toolName() + "\n" + observation.output()));
                        }
                        System.out.println("\nTool calls: " + answer.observations().size());
                        System.out.println("Live model call latency: " + answer.latencyMillis() + " ms");
                    }
                } else {
                    int limit = args.containsOption("top-k") ? Integer.parseInt(requiredSingleValue(args, "top-k")) : 5;
                    var evidence = retrievalService.retrieve(repository, question, diffReport, limit);
                    if (args.containsOption("answer")) {
                        result.put("mode", "vanilla-rag");
                        result.put("retrievedEvidence", evidence);
                        var answer = answerService.answer(question, diffReport, evidence);
                        result.put("answer", answer.text());
                        result.put("evidence", answer.evidence());
                        result.put("latencyMillis", answer.latencyMillis());
                        if (!json) {
                            System.out.println("### Vanilla RAG answer\n\n" + answer.text() + "\n");
                            System.out.println("Evidence map:");
                            answer.evidence().forEach(reference -> System.out.println("- [" + reference.id() + "] `"
                                    + reference.source() + ":" + reference.startLine() + "-" + reference.endLine() + "`"));
                            System.out.println("\nLive model call latency: " + answer.latencyMillis() + " ms");
                        }
                    } else {
                        result.put("mode", "retrieval");
                        result.put("retrievedEvidence", evidence);
                        if (!json) {
                            System.out.println(retrievalService.renderMarkdown(evidence));
                        }
                    }
                }
            } else {
                result.put("mode", "source-search");
                List<String> requestedTerms = args.getOptionValues("find");
                List<String> terms = requestedTerms == null ? List.of() : requestedTerms.stream()
                        .flatMap(value -> Arrays.stream(value.split(",")))
                        .toList();
                if (terms.isEmpty() && hasOld) {
                    terms = diffService.deriveImpactTerms(
                            Path.of(requiredSingleValue(args, "old")).toAbsolutePath().toString(),
                            Path.of(requiredSingleValue(args, "new")).toAbsolutePath().toString());
                }
                var matches = sourceSearchService.search(repository, terms);
                result.put("matches", matches);
                if (!json) {
                    System.out.println(sourceSearchService.renderMarkdown(matches));
                }
            }
            }
        } else if (args.containsOption("find") || hasQuestion || args.containsOption("top-k")
                || args.containsOption("answer") || args.containsOption("react")) {
            throw new IllegalArgumentException("--find, --question, --top-k, --answer, and --react require --repo=<local path or GitHub HTTPS URL>.");
        } else if (!hasOld) {
            result.put("message", "Provide --old and --new to compare OpenAPI specs, and/or --repo with --find terms to inspect Java source.");
            if (!json) {
                System.out.println(result.get("message"));
            }
        }
    }

    private Map<String, Object> baseResult(String mode) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", "1.0");
        result.put("status", "success");
        result.put("mode", mode);
        return result;
    }

    private String optionalSingleValue(ApplicationArguments args, String name, String defaultValue) {
        if (!args.containsOption(name)) {
            return defaultValue;
        }
        return requiredSingleValue(args, name);
    }

    private String requiredSingleValue(ApplicationArguments args, String name) {
        var values = args.getOptionValues(name);
        if (values == null || values.size() != 1 || values.getFirst().isBlank()) {
            throw new IllegalArgumentException("Provide exactly one --" + name + "=<value> argument.");
        }
        return values.getFirst();
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName() : message;
    }
}
