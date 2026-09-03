package com.recordly.recording;

import java.io.IOException;
import java.nio.file.Path;

public interface IRecordingSession {
    void recordPacket(byte[] packetBytes);
    void pause();
    void resume();
    boolean isPaused();
    boolean isRecording();
    int getDurationMillis();
    Path stopAndSave() throws IOException;
    void discard();
}
