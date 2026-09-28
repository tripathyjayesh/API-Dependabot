package org.apidependabot.diff;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiDiffServiceTest {
    private final OpenApiDiffService service = new OpenApiDiffService();

    @Test
    void reportsRemovedOperationAsDeleted() throws Exception {
        String report = service.compareAsMarkdown(fixture("v1.yaml"), fixture("v2-removed-operation.yaml"));

        assertThat(report).contains("What's Deleted", "GET` /widgets/{id}");
    }

    @Test
    void reportsAddedOperationAsNew() throws Exception {
        String report = service.compareAsMarkdown(fixture("v1.yaml"), fixture("v2-added-operation.yaml"));

        assertThat(report).contains("What's New", "POST` /widgets");
    }

    @Test
    void requiredRequestFieldIsReportedAsBreaking() throws Exception {
        String report = service.compareAsMarkdown(fixture("v1-with-create.yaml"), fixture("v2-required-request-field.yaml"));

        assertThat(report).contains("category", "API changes broke backward compatibility");
    }

    @Test
    void removedResponsePropertyIsReportedAsBreaking() throws Exception {
        String report = service.compareAsMarkdown(fixture("v1.yaml"), fixture("v2-removed-response-property.yaml"));

        assertThat(report).contains("Deleted property `name`", "API changes may break backward compatibility for existing consumers",
                "Consumer impact (review required)", "GET /widgets/{id} response 200 application/json",
                "removed response properties [name]");
    }

    @Test
    void removedNestedRequestAndResponseFieldsAreReportedAsConsumerBreaking() throws Exception {
        String report = service.compareAsMarkdown(fixture("v1-nested-consumer-fields.yaml"),
                fixture("v2-nested-consumer-fields.yaml"));

        assertThat(report)
                .contains("API changes may break backward compatibility for existing consumers",
                        "POST /subscription_schedules request application/x-www-form-urlencoded",
                        "removed request properties [phases[].iterations]",
                        "GET /subscriptions/{id} response 200 application/json",
                        "removed response properties [discounts[].coupon]")
                .doesNotContain("API changes are backward compatible");
    }

    @Test
    void renamedPathParameterIsReported() throws Exception {
        String report = service.compareAsMarkdown(fixture("v1.yaml"), fixture("v2-renamed-path-parameter.yaml"));

        assertThat(report).contains("Path parameter name changes", "/widgets/{id} -> /widgets/{widgetId}", "review generated/client argument bindings");
    }

    @Test
    void derivesRepositorySearchTermsFromChangedContractElements() {
        var terms = service.deriveImpactTerms(fixture("v1-with-create.yaml"), fixture("v2-required-request-field.yaml"));

        assertThat(terms).contains("category", "getCategory", "setCategory", "isCategory");
    }

    @Test
    void derivesBeanAccessorsForRemovedResponseProperties() {
        var terms = service.deriveImpactTerms(fixture("v1.yaml"), fixture("v2-removed-response-property.yaml"));

        assertThat(terms).contains("name", "getName", "setName", "isName");
    }

    @Test
    void derivesSearchTermsFromNestedRequestAndResponseRemovals() {
        var terms = service.deriveImpactTerms(fixture("v1-nested-consumer-fields.yaml"),
                fixture("v2-nested-consumer-fields.yaml"));

        assertThat(terms).contains("iterations", "getIterations", "coupon", "getCoupon", "discounts");
    }

    private String fixture(String name) {
        URL resource = getClass().getResource("/specs/" + name);
        assertThat(resource).as("fixture %s exists", name).isNotNull();
        try {
            return Path.of(resource.toURI()).toString();
        } catch (java.net.URISyntaxException exception) {
            throw new IllegalStateException("Invalid fixture URI: " + resource, exception);
        }
    }
}
