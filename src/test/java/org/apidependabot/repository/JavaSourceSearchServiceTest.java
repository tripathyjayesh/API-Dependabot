package org.apidependabot.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JavaSourceSearchServiceTest {
    private final JavaSourceSearchService service = new JavaSourceSearchService();

    @Test
    void returnsSourceAndTestHitsWithRepositoryRelativeLocations() throws Exception {
        List<JavaSourceSearchService.SearchHit> hits = service.search(fixtureRoot(), List.of("getName"));

        assertThat(hits).hasSize(3);
        assertThat(hits).extracting(JavaSourceSearchService.SearchHit::file)
                .containsExactly("src/main/java/demo/Widget.java", "src/main/java/demo/WidgetService.java",
                        "src/test/java/demo/WidgetServiceTest.java");
        assertThat(hits.getFirst().evidenceType()).isEqualTo("declaration");
        assertThat(hits).noneMatch(hit -> hit.file().contains("target/"));
        assertThat(hits).allSatisfy(hit -> assertThat(hit.line()).isPositive());
        assertThat(hits).allSatisfy(hit -> assertThat(hit.sourceLine()).contains("getName"));
    }

    @Test
    void reportsEmptySearchClearly() throws Exception {
        List<JavaSourceSearchService.SearchHit> hits = service.search(fixtureRoot(), List.of("notPresent"));

        assertThat(service.renderMarkdown(hits)).contains("No matches found.");
    }

    @Test
    void skipsGitDirectoriesDuringTraversal(@TempDir Path repository) throws Exception {
        Path packedJava = repository.resolve(".git/objects/pack/Hidden.java");
        Files.createDirectories(packedJava.getParent());
        Files.writeString(packedJava, "class Hidden { void secretToken() {} }");

        assertThat(service.search(repository, List.of("secretToken"))).isEmpty();
    }

    private Path fixtureRoot() throws Exception {
        URL resource = getClass().getResource("/consumer-project");
        assertThat(resource).isNotNull();
        return Path.of(resource.toURI());
    }
}
