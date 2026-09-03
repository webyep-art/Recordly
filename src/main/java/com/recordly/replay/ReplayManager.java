package com.recordly.replay;

import com.recordly.replay.camera.FreecamController;
import com.recordly.storage.RecordlyFile;
import com.recordly.storage.RecordlyMetadata;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public class ReplayManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReplayManager.class);
    private static final ReplayManager INSTANCE = new ReplayManager();

    private final FreecamController freecamController = new FreecamController();
    private RecordlyFile currentReplayFile;
    private RecordlyMetadata currentMetadata;
    private IReplayPlaybackController playbackController;
    private IPacketReader packetReader;
    private boolean inReplay = false;

    private ReplayManager() {
    }

    public static ReplayManager getInstance() {
        return INSTANCE;
    }

    public synchronized boolean startReplay(RecordlyFile file) {
        if (inReplay) {
            stopReplay();
        }

        Optional<RecordlyMetadata> metadataOpt = file.readMetadata();
        if (metadataOpt.isEmpty()) {
            LOGGER.error("Recordly: Missing metadata in {}", file.getName());
            return false;
        }

        try {
            Optional<InputStream> streamOpt = file.openPacketStream();
            if (streamOpt.isEmpty()) {
                LOGGER.error("Recordly: Missing packet stream in {}", file.getName());
                return false;
            }

            this.currentReplayFile = file;
            this.currentMetadata = metadataOpt.get();
            this.playbackController = new ReplayPlaybackController(currentMetadata.getDuration());
            this.packetReader = new PacketStreamReader(streamOpt.get());
            this.inReplay = true;

            freecamController.setActive(true);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                freecamController.setPosition(mc.player.position());
                freecamController.setRotation(mc.player.getYRot(), mc.player.getXRot());
            }

            LOGGER.info("Recordly: Started replay '{}', duration {}ms", file.getName(), currentMetadata.getDuration());
            return true;
        } catch (IOException e) {
            LOGGER.error("Recordly: Failed to open replay stream", e);
            stopReplay();
            return false;
        }
    }

    public synchronized void stopReplay() {
        if (!inReplay) {
            return;
        }
        inReplay = false;
        freecamController.setActive(false);

        if (packetReader != null) {
            try {
                packetReader.close();
            } catch (IOException ignored) {
            }
            packetReader = null;
        }
        playbackController = null;
        currentReplayFile = null;
        currentMetadata = null;
        LOGGER.info("Recordly: Stopped replay");
    }

    public boolean isInReplay() {
        return inReplay;
    }

    public FreecamController getFreecamController() {
        return freecamController;
    }

    public IReplayPlaybackController getPlaybackController() {
        return playbackController;
    }

    public RecordlyMetadata getCurrentMetadata() {
        return currentMetadata;
    }

    public RecordlyFile getCurrentReplayFile() {
        return currentReplayFile;
    }
}
