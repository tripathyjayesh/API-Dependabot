package org.apidependabot.repository;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class GitHubRepositoryServiceTest {
    private final GitHubRepositoryService service = new GitHubRepositoryService();

    @Test
    void parsesHttpsGitHubRepositoryWithBranch() {
        var reference = GitHubRepositoryService.parseGitHubReference("https://github.com/example/consumer#release/2.x");

        assertThat(reference.owner()).isEqualTo("example");
        assertThat(reference.repository()).isEqualTo("consumer");
        assertThat(reference.ref()).isEqualTo("release/2.x");
        assertThat(reference.cloneUrl()).isEqualTo("https://github.com/example/consumer.git");
    }

    @Test
    void acceptsPastedMarkdownLinkWithMatchingLabelAndTarget() {
        var reference = GitHubRepositoryService.parseGitHubReference(
                "[https://github.com/spring-projects/spring-petclinic](https://github.com/spring-projects/spring-petclinic)");

        assertThat(reference.owner()).isEqualTo("spring-projects");
        assertThat(reference.repository()).isEqualTo("spring-petclinic");
    }

    @Test
    void parsesGitSuffixAndDefaultBranch() {
        var reference = GitHubRepositoryService.parseGitHubReference("https://github.com/example/consumer.git");

        assertThat(reference.repository()).isEqualTo("consumer");
        assertThat(reference.ref()).isNull();
    }

    @Test
    void rejectsNonGitHubHostsAndEmbeddedCredentials() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                GitHubRepositoryService.parseGitHubReference("https://github.example.com/team/app"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                GitHubRepositoryService.parseGitHubReference("https://user:secret@github.com/team/app"));
    }

    @Test
    void rejectsQueryStringsAndMissingRepositorySegments() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                GitHubRepositoryService.parseGitHubReference("https://github.com/team/app?tab=readme"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                GitHubRepositoryService.parseGitHubReference("https://github.com/team"));
    }

    @Test
    void opensLocalRepositoryWithoutTakingOwnershipOfIt() throws Exception {
        Path fixture = Path.of("src/test/resources/consumer-project").toAbsolutePath();

        try (var checkout = service.open(fixture.toString())) {
            assertThat(checkout.path()).isEqualTo(fixture.toRealPath());
        }

        assertThat(fixture).isDirectory();
    }
}
