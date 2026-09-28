package org.apidependabot.answer;

import org.apidependabot.retrieval.LexicalRetrievalService;
import org.apidependabot.repository.JavaSourceSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReActRepositoryToolsTest {
    @TempDir
    Path repository;

    private ReActRepositoryTools tools;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(repository.resolve("src/main/java/demo"));
        Files.writeString(repository.resolve("src/main/java/demo/WidgetService.java"), """
                package demo;
                public class WidgetService {
                    public String display(Widget widget) {
                        return widget.getName();
                    }
                }
                """);
        tools = new ReActRepositoryTools(repository, "Removed response property name.",
                new JavaSourceSearchService(), new LexicalRetrievalService());
    }

    @Test
    void searchAndReadReturnBoundedEvidenceWithTraceIds() {
        String search = tools.searchJava("getName");
        String read = tools.readJavaFile("src/main/java/demo/WidgetService.java", 2);

        assertThat(search).contains("[T1]", "WidgetService.java:4", "widget.getName()");
        assertThat(read).contains("[T2]", "WidgetService.java:2-6", "4:         return widget.getName();");
        assertThat(tools.observations()).extracting(ReActRepositoryTools.ToolObservation::toolName)
                .containsExactly("searchJava", "readJavaFile");
    }

    @Test
    void readRejectsTraversalAndNonJavaFiles() {
        assertThat(tools.readJavaFile("../../outside.java", 1)).contains("Rejected path outside");
        assertThat(tools.readJavaFile("pom.xml", 1)).contains("Only Java files");
    }

    @Test
    void readDefaultsToFirstLineAndCapsOutputAtEightyLines() throws Exception {
        String source = java.util.stream.IntStream.rangeClosed(1, 100)
                .mapToObj(line -> "// line " + line)
                .collect(java.util.stream.Collectors.joining("\n"));
        Files.writeString(repository.resolve("src/main/java/demo/Large.java"), source);

        String read = tools.readJavaFile("src/main/java/demo/Large.java", null);

        assertThat(read).contains("Large.java:1-80", "1: // line 1", "80: // line 80")
                .doesNotContain("81: // line 81");
    }

    @Test
    void toolUseIsCappedAtEightCalls() {
        for (int call = 0; call < 8; call++) {
            tools.inspectOpenApiDiff();
        }

        assertThat(tools.searchJava("getName")).contains("tool budget exhausted");
        assertThat(tools.observations()).hasSize(8);
    }
}
