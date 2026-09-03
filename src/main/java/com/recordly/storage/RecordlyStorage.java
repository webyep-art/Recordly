package com.recordly.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

public class RecordlyStorage {
    private static final String DEFAULT_DIRECTORY = "recordings";
    private static final String EXTENSION = ".mcpr";
    private final Path storageDirectory;

    public RecordlyStorage(Path gameDirectory) {
        this.storageDirectory = gameDirectory.resolve(DEFAULT_DIRECTORY);
        ensureDirectoryExists();
    }

    private void ensureDirectoryExists() {
        if (!Files.exists(storageDirectory)) {
            try {
                Files.createDirectories(storageDirectory);
            } catch (IOException ignored) {
            }
        }
    }

    public Path getStorageDirectory() {
        return storageDirectory;
    }

    public List<RecordlyFile> listRecordings() {
        ensureDirectoryExists();
        List<RecordlyFile> result = new ArrayList<>();
        try (Stream<Path> stream = Files.list(storageDirectory)) {
            stream.filter(path -> path.getFileName().toString().endsWith(EXTENSION))
                  .sorted((p1, p2) -> {
                      try {
                          return Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1));
                      } catch (IOException e) {
                          return 0;
                      }
                  })
                  .forEach(path -> result.add(new RecordlyFile(path)));
        } catch (IOException e) {
            return Collections.emptyList();
        }
        return result;
    }

    public Path createNewRecordingPath(String baseName) {
        ensureDirectoryExists();
        String timestamp = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date());
        String sanitized = baseName.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        String filename = sanitized + "_" + timestamp + EXTENSION;
        return storageDirectory.resolve(filename);
    }

    public Path createTempPacketPath() throws IOException {
        Path tempDir = storageDirectory.resolve(".temp");
        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir);
        }
        return Files.createTempFile(tempDir, "recordly_stream_", ".tmcpr");
    }
}
