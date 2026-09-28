package org.apidependabot.retrieval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LexicalRetrievalServiceTest {
    private final LexicalRetrievalService service = new LexicalRetrievalService();

    @Test
    void retrievesRelevantJavaEvidenceWithSourceLines() throws Exception {
        List<LexicalRetrievalService.RetrievedChunk> results = service.retrieve(fixtureRoot(),
                "Where does the Java consumer read the widget name?",
                "Removed response property name from GET /widgets/{id}.", 3);

        assertThat(results).isNotEmpty();
        assertThat(results).extracting(LexicalRetrievalService.RetrievedChunk::source)
                .contains("src/main/java/demo/WidgetService.java", "src/test/java/demo/WidgetServiceTest.java");
        assertThat(results).anySatisfy(chunk -> {
            assertThat(chunk.source()).isEqualTo("src/main/java/demo/WidgetService.java");
            assertThat(chunk.startLine()).isPositive();
            assertThat(chunk.endLine()).isGreaterThanOrEqualTo(chunk.startLine());
            assertThat(chunk.content()).contains("widget.getName()");
        });
    }

    @Test
    void ranksResultsDeterministically() throws Exception {
        List<LexicalRetrievalService.RetrievedChunk> first = service.retrieve(fixtureRoot(),
                "Where does the Java consumer read the widget name?", "", 5);
        List<LexicalRetrievalService.RetrievedChunk> second = service.retrieve(fixtureRoot(),
                "Where does the Java consumer read the widget name?", "", 5);

        assertThat(first).containsExactlyElementsOf(second);
    }

    @Test
    void doesNotIndexGitDirectoryContent(@TempDir Path repository) throws Exception {
        Path packedReadme = repository.resolve(".git/objects/pack/README.md");
        Files.createDirectories(packedReadme.getParent());
        Files.writeString(packedReadme, "packedObjectsOnlyContainVersionControlInternals");

        var results = service.retrieve(repository, "packed objects version control internals", "", 10);

        assertThat(results).noneMatch(chunk -> chunk.source().startsWith(".git/"));
    }

    private Path fixtureRoot() throws Exception {
        URL resource = getClass().getResource("/consumer-project");
        assertThat(resource).isNotNull();
        return Path.of(resource.toURI());
    }
}
