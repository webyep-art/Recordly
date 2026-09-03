package com.recordly.storage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class RecordlyFile {
    private final Path filePath;

    public RecordlyFile(Path filePath) {
        this.filePath = filePath;
    }

    public Path getFilePath() {
        return filePath;
    }

    public String getName() {
        return filePath.getFileName().toString();
    }

    public boolean exists() {
        return Files.exists(filePath);
    }

    public long getSize() throws IOException {
        return Files.size(filePath);
    }

    public Optional<RecordlyMetadata> readMetadata() {
        if (!exists()) {
            return Optional.empty();
        }
        try (ZipFile zipFile = new ZipFile(filePath.toFile())) {
            ZipEntry entry = zipFile.getEntry("metaData.json");
            if (entry == null) {
                return Optional.empty();
            }
            try (InputStream stream = zipFile.getInputStream(entry)) {
                return Optional.of(RecordlyMetadata.fromJson(stream));
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<InputStream> openPacketStream() throws IOException {
        if (!exists()) {
            return Optional.empty();
        }
        ZipFile zipFile = new ZipFile(filePath.toFile());
        ZipEntry entry = zipFile.getEntry("recording.tmcpr");
        if (entry == null) {
            zipFile.close();
            return Optional.empty();
        }
        InputStream stream = zipFile.getInputStream(entry);
        return Optional.of(new BufferedInputStream(stream) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    zipFile.close();
                }
            }
        });
    }

    public void createArchive(Path tempPacketFile, RecordlyMetadata metadata) throws IOException {
        Path parent = filePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(filePath.toFile())))) {
            zos.putNextEntry(new ZipEntry("recording.tmcpr"));
            try (InputStream in = new BufferedInputStream(new FileInputStream(tempPacketFile.toFile()))) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    zos.write(buffer, 0, read);
                }
            }
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("metaData.json"));
            try {
                metadata.writeTo(zos);
            } catch (Exception e) {
                throw new IOException(e);
            }
            zos.closeEntry();
        }
    }

    public boolean delete() {
        try {
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            return false;
        }
    }
}
