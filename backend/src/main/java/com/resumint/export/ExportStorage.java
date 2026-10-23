package com.resumint.export;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Set;
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
    public long usableSpace() {
        try { Files.createDirectories(root); return Files.getFileStore(root).getUsableSpace(); }
        catch (IOException exception) { throw new IllegalStateException("cannot inspect export storage", exception); }
    }
    public int cleanupOrphans(Set<String> referenced, Instant olderThan) {
        int deleted = 0;
        for (String directory : java.util.List.of("snapshots", "results")) {
            Path folder = root.resolve(directory);
            if (!Files.isDirectory(folder)) continue;
            try (var paths = Files.list(folder)) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) {
                    String ref = root.relativize(path).toString().replace('\\', '/');
                    FileTime modified = Files.getLastModifiedTime(path);
                    if (!referenced.contains(ref) && modified.toInstant().isBefore(olderThan)) {
                        Files.deleteIfExists(path);
                        deleted++;
                    }
                }
            } catch (IOException exception) {
                throw new IllegalStateException("cannot reconcile export storage", exception);
            }
        }
        return deleted;
    }
    private Path resolve(String ref) {
        Path path = root.resolve(ref).normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("invalid private storage reference");
        return path;
    }
}
