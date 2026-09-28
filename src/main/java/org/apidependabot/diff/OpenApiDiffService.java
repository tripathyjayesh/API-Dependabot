package org.apidependabot.diff;

import org.openapitools.openapidiff.core.OpenApiCompare;
import org.openapitools.openapidiff.core.model.ChangedOpenApi;
import org.openapitools.openapidiff.core.output.MarkdownRender;
import org.springframework.stereotype.Service;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OpenApiDiffService {
    private static final Pattern PATH_PARAMETER = Pattern.compile("\\{([^/{}]+)}");

    public String compareAsMarkdown(String oldSpec, String newSpec) throws IOException {
        OpenAPI oldApi = parse(oldSpec);
        OpenAPI newApi = parse(newSpec);
        ChangedOpenApi diff = OpenApiCompare.fromLocations(oldSpec, newSpec);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        OutputStreamWriter output = new OutputStreamWriter(bytes, StandardCharsets.UTF_8);
        new MarkdownRender().render(diff, output);
        String report = bytes.toString(StandardCharsets.UTF_8);
        List<String> requestNotes = removedRequestPropertyNotes(oldApi, newApi);
        List<String> responseNotes = removedResponsePropertyNotes(oldApi, newApi);
        if (!requestNotes.isEmpty() || !responseNotes.isEmpty()) {
            report = report.replace("API changes are backward compatible",
                    "API changes may break backward compatibility for existing consumers");
        }
        return report + pathParameterNotes(oldApi, newApi)
                + consumerImpactNotes(requestNotes, responseNotes);
    }

    public List<String> deriveImpactTerms(String oldSpec, String newSpec) {
        OpenAPI oldApi = parse(oldSpec);
        OpenAPI newApi = parse(newSpec);
        Set<String> terms = new LinkedHashSet<>();

        Map<String, ResponseProperties> oldResponses = responseProperties(oldApi);
        Map<String, ResponseProperties> newResponses = responseProperties(newApi);
        oldResponses.forEach((key, oldResponse) -> {
            ResponseProperties current = newResponses.get(key);
            if (current != null) {
                Set<String> removed = new LinkedHashSet<>(oldResponse.properties());
                removed.removeAll(current.properties());
                removed.forEach(property -> addFieldPathTerms(terms, property));
            }
        });
        Map<String, RequestProperties> oldRequests = requestProperties(oldApi);
        Map<String, RequestProperties> newRequests = requestProperties(newApi);
        oldRequests.forEach((key, oldRequest) -> {
            RequestProperties current = newRequests.get(key);
            if (current != null) {
                Set<String> removed = new LinkedHashSet<>(oldRequest.properties());
                removed.removeAll(current.properties());
                removed.forEach(property -> addFieldPathTerms(terms, property));
            }
        });

        if (oldApi.getPaths() == null || newApi.getPaths() == null) {
            return List.copyOf(terms);
        }
        for (Map.Entry<String, PathItem> newPath : newApi.getPaths().entrySet()) {
            List<Map.Entry<String, PathItem>> oldMatches = oldApi.getPaths().entrySet().stream()
                    .filter(entry -> normalizePath(entry.getKey()).equals(normalizePath(newPath.getKey())))
                    .toList();
            if (oldMatches.isEmpty()) {
                newPath.getValue().readOperationsMap().values()
                        .forEach(operation -> addOperationTerms(terms, operation, newPath.getKey()));
                continue;
            }
            if (oldMatches.size() != 1) {
                continue;
            }
            Map.Entry<String, PathItem> oldPath = oldMatches.getFirst();
            List<String> oldNames = parameterNames(oldPath.getKey());
            List<String> newNames = parameterNames(newPath.getKey());
            if (oldNames.size() == newNames.size()) {
                boolean renamedParameter = false;
                for (int i = 0; i < oldNames.size(); i++) {
                    if (!oldNames.get(i).equals(newNames.get(i))) {
                        renamedParameter = true;
                        addPropertyTerms(terms, oldNames.get(i));
                        addPropertyTerms(terms, newNames.get(i));
                    }
                }
                if (renamedParameter) {
                    oldPath.getValue().readOperationsMap().forEach((method, operation) -> {
                        if (newPath.getValue().readOperationsMap().containsKey(method)) {
                            addOperationId(terms, operation);
                            addOperationId(terms, newPath.getValue().readOperationsMap().get(method));
                        }
                    });
                }
            }

            for (Map.Entry<PathItem.HttpMethod, Operation> operation : newPath.getValue().readOperationsMap().entrySet()) {
                Operation oldOperation = oldPath.getValue().readOperationsMap().get(operation.getKey());
                if (oldOperation != null) {
                    addRequestFieldTerms(oldApi, newApi, oldOperation, operation.getValue(), terms);
                    continue;
                }
                addOperationTerms(terms, operation.getValue(), newPath.getKey());
            }
            for (Map.Entry<PathItem.HttpMethod, Operation> operation : oldPath.getValue().readOperationsMap().entrySet()) {
                if (!newPath.getValue().readOperationsMap().containsKey(operation.getKey())) {
                    addOperationTerms(terms, operation.getValue(), oldPath.getKey());
                }
            }
        }
        for (Map.Entry<String, PathItem> oldPath : oldApi.getPaths().entrySet()) {
            boolean stillExists = newApi.getPaths().keySet().stream()
                    .anyMatch(path -> normalizePath(path).equals(normalizePath(oldPath.getKey())));
            if (!stillExists) {
                oldPath.getValue().readOperationsMap().values()
                        .forEach(operation -> addOperationTerms(terms, operation, oldPath.getKey()));
            }
        }
        return List.copyOf(terms);
    }

    private OpenAPI parse(String location) {
        SwaggerParseResult parsed = new OpenAPIV3Parser().readLocation(location, null, new ParseOptions());
        if (parsed.getOpenAPI() == null) {
            throw new IllegalArgumentException("Unable to parse OpenAPI document " + location + ": "
                    + String.join("; ", parsed.getMessages()));
        }
        return parsed.getOpenAPI();
    }

    private String pathParameterNotes(OpenAPI oldApi, OpenAPI newApi) {
        Map<String, PathItem> oldPaths = oldApi.getPaths();
        Map<String, PathItem> newPaths = newApi.getPaths();
        if (oldPaths == null || newPaths == null) {
            return "";
        }

        List<String> notes = new ArrayList<>();
        for (Map.Entry<String, PathItem> oldEntry : oldPaths.entrySet()) {
            String oldPath = oldEntry.getKey();
            List<String> oldNames = parameterNames(oldPath);
            String normalized = normalizePath(oldPath);
            List<Map.Entry<String, PathItem>> matches = newPaths.entrySet().stream()
                    .filter(entry -> normalizePath(entry.getKey()).equals(normalized))
                    .toList();
            if (oldNames.isEmpty() || matches.size() != 1) {
                continue;
            }

            Map.Entry<String, PathItem> match = matches.getFirst();
            List<String> newNames = parameterNames(match.getKey());
            if (oldNames.size() != newNames.size() || oldNames.equals(newNames)) {
                continue;
            }
            List<String> methods = oldEntry.getValue().readOperationsMap().keySet().stream()
                    .filter(match.getValue().readOperationsMap().keySet()::contains)
                    .map(method -> method.name())
                    .sorted()
                    .toList();
            for (String method : methods) {
                notes.add("- `" + method + "` " + oldPath + " -> " + match.getKey()
                        + ": path parameter name changed; review generated/client argument bindings.");
            }
        }
        if (notes.isEmpty()) {
            return "";
        }
        return "\n#### Path parameter name changes\n\n" + String.join("\n", notes) + "\n";
    }

    private List<String> parameterNames(String path) {
        Matcher matcher = PATH_PARAMETER.matcher(path);
        List<String> names = new ArrayList<>();
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private String normalizePath(String path) {
        return PATH_PARAMETER.matcher(path).replaceAll("{}");
    }

    private void addPropertyTerms(Set<String> terms, String property) {
        if (property == null || property.isBlank()) {
            return;
        }
        terms.add(property);
        String suffix = Character.toUpperCase(property.charAt(0)) + property.substring(1);
        terms.add("get" + suffix);
        terms.add("set" + suffix);
        terms.add("is" + suffix);
    }

    private void addFieldPathTerms(Set<String> terms, String fieldPath) {
        for (String property : fieldPath.split("[.\\[\\]]+")) {
            addPropertyTerms(terms, property);
        }
    }

    private void addOperationTerms(Set<String> terms, Operation operation, String path) {
        addOperationId(terms, operation);
        for (String segment : path.split("/")) {
            if (!segment.isBlank() && !segment.startsWith("{")) {
                terms.add(segment);
            }
        }
        parameterNames(path).forEach(name -> addPropertyTerms(terms, name));
    }

    private void addOperationId(Set<String> terms, Operation operation) {
        if (operation.getOperationId() != null) {
            terms.add(operation.getOperationId());
        }
    }

    private void addRequestFieldTerms(OpenAPI oldApi, OpenAPI newApi, Operation oldOperation,
                                      Operation newOperation, Set<String> terms) {
        if (newOperation.getRequestBody() == null || newOperation.getRequestBody().getContent() == null) {
            return;
        }
        newOperation.getRequestBody().getContent().forEach((mediaType, body) -> {
            Schema<?> newSchema = resolveSchema(newApi, body.getSchema());
            Schema<?> oldSchema = oldOperation.getRequestBody() == null
                    || oldOperation.getRequestBody().getContent() == null
                    || oldOperation.getRequestBody().getContent().get(mediaType) == null
                    ? null
                    : resolveSchema(oldApi, oldOperation.getRequestBody().getContent().get(mediaType).getSchema());
            if (newSchema == null || newSchema.getRequired() == null) {
                return;
            }
            Set<String> oldRequired = oldSchema == null || oldSchema.getRequired() == null
                    ? Set.of() : new LinkedHashSet<>(oldSchema.getRequired());
            newSchema.getRequired().stream().filter(required -> !oldRequired.contains(required))
                    .forEach(required -> addPropertyTerms(terms, required));
        });
    }

    private List<String> removedRequestPropertyNotes(OpenAPI oldApi, OpenAPI newApi) {
        Map<String, RequestProperties> oldProperties = requestProperties(oldApi);
        Map<String, RequestProperties> newProperties = requestProperties(newApi);
        List<String> notes = new ArrayList<>();
        for (Map.Entry<String, RequestProperties> entry : oldProperties.entrySet()) {
            RequestProperties oldRequest = entry.getValue();
            RequestProperties current = newProperties.get(entry.getKey());
            if (current == null) {
                continue;
            }
            Set<String> removed = new LinkedHashSet<>(oldRequest.properties());
            removed.removeAll(current.properties());
            if (!removed.isEmpty()) {
                notes.add("- " + oldRequest.method() + " " + oldRequest.path() + " request "
                        + oldRequest.mediaType() + ": removed request properties " + minimalRemovedPaths(removed)
                        + "; consumers sending these fields need migration review.");
            }
        }
        return notes;
    }

    private List<String> removedResponsePropertyNotes(OpenAPI oldApi, OpenAPI newApi) {
        Map<String, ResponseProperties> oldProperties = responseProperties(oldApi);
        Map<String, ResponseProperties> newProperties = responseProperties(newApi);
        List<String> notes = new ArrayList<>();
        for (Map.Entry<String, ResponseProperties> entry : oldProperties.entrySet()) {
            ResponseProperties oldResponse = entry.getValue();
            ResponseProperties current = newProperties.get(entry.getKey());
            if (current == null) {
                continue;
            }
            Set<String> removed = new LinkedHashSet<>(oldResponse.properties());
            removed.removeAll(current.properties());
            if (!removed.isEmpty()) {
                notes.add("- " + oldResponse.method() + " " + oldResponse.path() + " response "
                        + oldResponse.status() + " " + oldResponse.mediaType() + ": removed response properties "
                        + minimalRemovedPaths(removed) + "; review consumer references.");
            }
        }
        return notes;
    }

    private String consumerImpactNotes(List<String> requestNotes, List<String> responseNotes) {
        List<String> notes = new ArrayList<>(requestNotes);
        notes.addAll(responseNotes);
        if (notes.isEmpty()) {
            return "";
        }
        return "\n#### Consumer impact (review required)\n\n" + String.join("\n", notes) + "\n";
    }

    private Set<String> minimalRemovedPaths(Set<String> removed) {
        return removed.stream()
                .filter(candidate -> removed.stream().noneMatch(parent -> !parent.equals(candidate)
                        && (candidate.startsWith(parent + ".") || candidate.startsWith(parent + "[]"))))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<String, ResponseProperties> responseProperties(OpenAPI api) {
        Map<String, ResponseProperties> propertiesByResponse = new java.util.LinkedHashMap<>();
        if (api.getPaths() == null) {
            return propertiesByResponse;
        }
        api.getPaths().forEach((path, pathItem) -> pathItem.readOperationsMap().forEach((method, operation) -> {
            if (operation.getResponses() == null) {
                return;
            }
            operation.getResponses().forEach((status, response) -> {
                Content content = response.getContent();
                if (content == null) {
                    return;
                }
                content.forEach((mediaTypeName, mediaType) -> {
                    Schema<?> schema = resolveSchema(api, mediaType.getSchema());
                    if (schema != null && schema.getProperties() != null) {
                        String key = method.name() + " " + normalizePath(path) + " response " + status
                                + " " + mediaTypeName;
                        Set<String> fieldPaths = new LinkedHashSet<>();
                        collectFieldPaths(api, schema, "", fieldPaths, new LinkedHashSet<>());
                        propertiesByResponse.put(key, new ResponseProperties(method.name(), path, status,
                                mediaTypeName, fieldPaths));
                    }
                });
            });
        }));
        return propertiesByResponse;
    }

    private Map<String, RequestProperties> requestProperties(OpenAPI api) {
        Map<String, RequestProperties> propertiesByRequest = new java.util.LinkedHashMap<>();
        if (api.getPaths() == null) {
            return propertiesByRequest;
        }
        api.getPaths().forEach((path, pathItem) -> pathItem.readOperationsMap().forEach((method, operation) -> {
            if (operation.getRequestBody() == null || operation.getRequestBody().getContent() == null) {
                return;
            }
            operation.getRequestBody().getContent().forEach((mediaTypeName, mediaType) -> {
                Set<String> fieldPaths = new LinkedHashSet<>();
                collectFieldPaths(api, mediaType.getSchema(), "", fieldPaths, new LinkedHashSet<>());
                String key = method.name() + " " + normalizePath(path) + " request " + mediaTypeName;
                propertiesByRequest.put(key, new RequestProperties(method.name(), path, mediaTypeName, fieldPaths));
            });
        }));
        return propertiesByRequest;
    }

    private void collectFieldPaths(OpenAPI api, Schema<?> unresolvedSchema, String prefix,
                                   Set<String> fieldPaths, Set<String> visitedRefs) {
        if (unresolvedSchema == null) {
            return;
        }
        Schema<?> schema = unresolvedSchema;
        Set<String> refsOnPath = new LinkedHashSet<>(visitedRefs);
        if (schema.get$ref() != null) {
            if (!refsOnPath.add(schema.get$ref())) {
                return;
            }
            schema = resolveSchema(api, schema);
            if (schema == null) {
                return;
            }
        }
        if (schema.getProperties() != null) {
            schema.getProperties().forEach((name, propertySchema) -> {
                String fieldPath = prefix.isEmpty() ? name : prefix + "." + name;
                fieldPaths.add(fieldPath);
                collectFieldPaths(api, (Schema<?>) propertySchema, fieldPath, fieldPaths, refsOnPath);
            });
        }
        if (schema.getItems() != null) {
            collectFieldPaths(api, schema.getItems(), prefix + "[]", fieldPaths, refsOnPath);
        }
        collectComposedFieldPaths(api, schema.getAllOf(), prefix, fieldPaths, refsOnPath);
        collectComposedFieldPaths(api, schema.getOneOf(), prefix, fieldPaths, refsOnPath);
        collectComposedFieldPaths(api, schema.getAnyOf(), prefix, fieldPaths, refsOnPath);
    }

    private void collectComposedFieldPaths(OpenAPI api, List<Schema> schemas, String prefix,
                                          Set<String> fieldPaths, Set<String> visitedRefs) {
        if (schemas == null) {
            return;
        }
        schemas.forEach(schema -> collectFieldPaths(api, schema, prefix, fieldPaths, visitedRefs));
    }

    private Schema<?> resolveSchema(OpenAPI api, Schema<?> schema) {
        if (schema == null || schema.get$ref() == null) {
            return schema;
        }
        String prefix = "#/components/schemas/";
        if (!schema.get$ref().startsWith(prefix) || api.getComponents() == null
                || api.getComponents().getSchemas() == null) {
            return null;
        }
        return api.getComponents().getSchemas().get(schema.get$ref().substring(prefix.length()));
    }

    private record RequestProperties(String method, String path, String mediaType, Set<String> properties) { }
    private record ResponseProperties(String method, String path, String status, String mediaType,
                                      Set<String> properties) { }
}
