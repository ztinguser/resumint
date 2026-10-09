package com.resumint.export;

import tools.jackson.databind.JsonNode;

public record ExportSnapshot(JsonNode document) {
}
