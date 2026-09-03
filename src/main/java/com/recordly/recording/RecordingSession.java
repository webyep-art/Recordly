package com.recordly.recording;

import com.recordly.storage.RecordlyFile;
import com.recordly.storage.RecordlyMetadata;
import com.recordly.storage.RecordlyStorage;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class RecordingSession implements IRecordingSession {
    private final RecordlyStorage storage;
    private final RecordlyMetadata metadata;
    private final Path tempPacketPath;
    private final IPacketWriter packetWriter;
    private final ExecutorService ioExecutor;

    private final long startTimeMillis;
    private long pausedAtMillis = 0;
    private long totalPausedMillis = 0;
    private int lastRecordedTimestamp = 0;

    private final AtomicBoolean recording = new AtomicBoolean(true);
    private final AtomicBoolean paused = new AtomicBoolean(false);

    public RecordingSession(RecordlyStorage storage, String sessionName, boolean singleplayer) throws IOException {
        this.storage = storage;
        this.metadata = new RecordlyMetadata();
        this.metadata.setServerName(sessionName);
        this.metadata.setSingleplayer(singleplayer);
        this.metadata.setDate(System.currentTimeMillis());

        this.tempPacketPath = storage.createTempPacketPath();
        this.packetWriter = new PacketStreamWriter(new FileOutputStream(tempPacketPath.toFile()));
        this.ioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Recordly-IO-Writer");
            t.setDaemon(true);
            return t;
        });
        this.startTimeMillis = System.currentTimeMillis();
    }

    @Override
    public void recordPacket(byte[] packetBytes) {
        if (!recording.get() || paused.get() || packetBytes == null || packetBytes.length == 0) {
            return;
        }
        long now = System.currentTimeMillis();
        int timestamp = (int) (now - startTimeMillis - totalPausedMillis);
        if (timestamp < lastRecordedTimestamp) {
            timestamp = lastRecordedTimestamp;
        }
        lastRecordedTimestamp = timestamp;

        final int packetTime = timestamp;
        ioExecutor.submit(() -> {
            try {
                packetWriter.writePacket(packetTime, packetBytes);
            } catch (IOException ignored) {
            }
        });
    }

    @Override
    public void pause() {
        if (recording.get() && paused.compareAndSet(false, true)) {
            pausedAtMillis = System.currentTimeMillis();
        }
    }

    @Override
    public void resume() {
        if (recording.get() && paused.compareAndSet(true, false)) {
            totalPausedMillis += (System.currentTimeMillis() - pausedAtMillis);
        }
    }

    @Override
    public boolean isPaused() {
        return paused.get();
    }

    @Override
    public boolean isRecording() {
        return recording.get();
    }

    @Override
    public int getDurationMillis() {
        if (!recording.get()) {
            return lastRecordedTimestamp;
        }
        long now = paused.get() ? pausedAtMillis : System.currentTimeMillis();
        return (int) Math.max(0, now - startTimeMillis - totalPausedMillis);
    }

    @Override
    public Path stopAndSave() throws IOException {
        if (!recording.compareAndSet(true, false)) {
            throw new IllegalStateException("Recording already stopped");
        }

        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        packetWriter.flush();
        packetWriter.close();

        metadata.setDuration(lastRecordedTimestamp);

        Path finalPath = storage.createNewRecordingPath(metadata.getServerName());
        RecordlyFile recordlyFile = new RecordlyFile(finalPath);
        recordlyFile.createArchive(tempPacketPath, metadata);

        Files.deleteIfExists(tempPacketPath);

        return finalPath;
    }

    @Override
    public void discard() {
        recording.set(false);
        ioExecutor.shutdownNow();
        try {
            packetWriter.close();
        } catch (IOException ignored) {
        }
        try {
            Files.deleteIfExists(tempPacketPath);
        } catch (IOException ignored) {
        }
    }
}
