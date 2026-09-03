package com.recordly.recording;

import com.recordly.storage.RecordlyStorage;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public class RecordingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecordingManager.class);
    private static final RecordingManager INSTANCE = new RecordingManager();

    private RecordlyStorage storage;
    private IRecordingSession currentSession;
    private boolean autoRecordEnabled = true;

    private RecordingManager() {
    }

    public static RecordingManager getInstance() {
        return INSTANCE;
    }

    public void initialize(RecordlyStorage storage) {
        this.storage = storage;
    }

    public synchronized void startRecording(String sessionName, boolean singleplayer) {
        if (currentSession != null && currentSession.isRecording()) {
            return;
        }
        if (storage == null) {
            return;
        }
        try {
            currentSession = new RecordingSession(storage, sessionName, singleplayer);
            LOGGER.info("Recordly: Started recording session '{}'", sessionName);
        } catch (IOException e) {
            LOGGER.error("Recordly: Failed to start recording session", e);
        }
    }

    public synchronized void recordPacket(byte[] packetBytes) {
        if (currentSession != null && currentSession.isRecording()) {
            currentSession.recordPacket(packetBytes);
        }
    }

    public synchronized Optional<Path> stopRecording() {
        if (currentSession == null || !currentSession.isRecording()) {
            return Optional.empty();
        }
        try {
            Path savedPath = currentSession.stopAndSave();
            LOGGER.info("Recordly: Saved recording to {}", savedPath);
            currentSession = null;
            return Optional.of(savedPath);
        } catch (IOException e) {
            LOGGER.error("Recordly: Failed to save recording", e);
            currentSession = null;
            return Optional.empty();
        }
    }

    public synchronized void pauseRecording() {
        if (currentSession != null) {
            currentSession.pause();
        }
    }

    public synchronized void resumeRecording() {
        if (currentSession != null) {
            currentSession.resume();
        }
    }

    public synchronized boolean isRecording() {
        return currentSession != null && currentSession.isRecording() && !currentSession.isPaused();
    }

    public synchronized boolean isPaused() {
        return currentSession != null && currentSession.isPaused();
    }

    public synchronized int getCurrentDurationMillis() {
        return currentSession != null ? currentSession.getDurationMillis() : 0;
    }

    public boolean isAutoRecordEnabled() {
        return autoRecordEnabled;
    }

    public void setAutoRecordEnabled(boolean autoRecordEnabled) {
        this.autoRecordEnabled = autoRecordEnabled;
    }
}
