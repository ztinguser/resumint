package com.resumint.export;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class ExportStorage {
    private final Path root;
    private final ObjectMapper objectMapper;
    public ExportStorage(Path root, ObjectMapper objectMapper) { this.root = root.toAbsolutePath().normalize(); this.objectMapper = objectMapper; }
    public String writeSnapshot(UUID id, JsonNode document) {
        String ref = "snapshots/" + id + ".json";
        try { Path path = resolve(ref); Files.createDirectories(path.getParent()); objectMapper.writeValue(path.toFile(), document); return ref; }
        catch (IOException exception) { throw new IllegalStateException("cannot persist export snapshot", exception); }
    }
    public ExportSnapshot readSnapshot(String ref) throws IOException { return new ExportSnapshot(objectMapper.readTree(resolve(ref).toFile())); }
    public String resultRef(UUID id) { return "results/" + id + ".pdf"; }
    public Path resultPath(String ref) throws IOException { Path path = resolve(ref); Files.createDirectories(path.getParent()); return path; }
    public Path temporaryResultPath(UUID id) throws IOException { Path path = resolve("results/" + id + ".pdf.tmp"); Files.createDirectories(path.getParent()); return path; }
    public void deleteQuietly(String ref) { if (ref == null) return; try { Files.deleteIfExists(resolve(ref)); } catch (IOException ignored) { } }
    public boolean exists(String ref) { return ref != null && Files.isRegularFile(resolve(ref)); }
    private Path resolve(String ref) {
        Path path = root.resolve(ref).normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("invalid private storage reference");
        return path;
    }
}
