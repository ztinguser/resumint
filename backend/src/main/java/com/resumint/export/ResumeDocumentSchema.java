package com.resumint.export;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class ResumeDocumentSchema {
    private final JsonNode schema;
    private final List<String> requiredFields;

    public ResumeDocumentSchema(ObjectMapper objectMapper) {
        try (InputStream input = Objects.requireNonNull(
                getClass().getResourceAsStream("/schema/resume-document.schema.json"),
                "Shared resume schema was not packaged")) {
            schema = objectMapper.readTree(input);
            var fields = new ArrayList<String>();
            schema.path("required").forEach(node -> fields.add(node.asString()));
            requiredFields = List.copyOf(fields);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load shared resume schema", exception);
        }
    }

    public void validateTopLevel(JsonNode document) {
        if (!document.isObject()) throw new IllegalArgumentException("document must be an object");
        for (String field : requiredFields) {
            if (!document.has(field)) throw new IllegalArgumentException("document is missing " + field);
        }
        if (!schema.path("properties").path("schemaVersion").path("const").asString().equals(document.path("schemaVersion").asString())) {
            throw new IllegalArgumentException("unsupported schemaVersion");
        }
        if (!document.path("sections").isArray() || document.path("sections").isEmpty()) {
            throw new IllegalArgumentException("document must contain at least one section");
        }
    }

    public JsonNode schema() { return schema; }
}
