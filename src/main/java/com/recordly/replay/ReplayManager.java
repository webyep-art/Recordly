package com.recordly.replay;

import com.recordly.replay.camera.FreecamController;
import com.recordly.replay.network.DropOutboundHandler;
import com.recordly.storage.RecordlyFile;
import com.recordly.storage.RecordlyMetadata;
import com.recordly.ui.screen.ReplayLoadingScreen;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.login.LoginProtocols;
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
    private PacketPayload nextPacket;
    private EmbeddedChannel channel;
    private Connection connection;
    private boolean inReplay = false;

    private ReplayManager() {
    }

    public static ReplayManager getInstance() {
        return INSTANCE;
    }

    public synchronized boolean startReplay(RecordlyFile file, Screen parentScreen) {
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
            this.nextPacket = packetReader.readNextPacket().orElse(null);

            Minecraft mc = Minecraft.getInstance();

            this.channel = new EmbeddedChannel();
            this.channel.pipeline().addFirst("drop_outbound", new DropOutboundHandler());
            Connection.configureInMemoryPipeline(this.channel.pipeline(), PacketFlow.CLIENTBOUND);

            this.connection = new Connection(PacketFlow.CLIENTBOUND);
            this.channel.pipeline().addLast("packet_handler", connection);
            this.channel.pipeline().fireChannelActive();

            ClientHandshakePacketListenerImpl listener = new ClientHandshakePacketListenerImpl(
                    connection,
                    mc,
                    null,
                    parentScreen,
                    false,
                    null,
                    status -> {},
                    null
            );
            connection.setupInboundProtocol(LoginProtocols.CLIENTBOUND, listener);

            this.inReplay = true;
            mc.setScreen(new ReplayLoadingScreen(parentScreen, file.getName()));

            LOGGER.info("Recordly: Started replay loading for '{}'", file.getName());
            return true;
        } catch (IOException e) {
            LOGGER.error("Recordly: Failed to open replay", e);
            stopReplay();
            return false;
        }
    }

    public synchronized void pumpInitialPackets() {
        if (!inReplay || channel == null || packetReader == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int pumped = 0;
        while (inReplay && (mc.level == null || pumped < 200)) {
            if (nextPacket == null) {
                if (packetReader.hasNext()) {
                    try {
                        nextPacket = packetReader.readNextPacket().orElse(null);
                    } catch (IOException e) {
                        break;
                    }
                }
                if (nextPacket == null) {
                    break;
                }
            }

            channel.pipeline().fireChannelRead(Unpooled.wrappedBuffer(nextPacket.data()));
            pumped++;

            try {
                nextPacket = packetReader.readNextPacket().orElse(null);
            } catch (IOException e) {
                nextPacket = null;
                break;
            }

            if (mc.level != null && mc.player != null) {
                break;
            }
        }

        if (mc.level != null && mc.player != null) {
            freecamController.setActive(true);
            freecamController.setPosition(mc.player.position());
            freecamController.setRotation(mc.player.getYRot(), mc.player.getXRot());
        }
    }

    public synchronized void tickPlayback() {
        if (!inReplay || channel == null || playbackController == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        playbackController.update(50);
        if (playbackController.isPaused()) {
            return;
        }

        int currentTime = playbackController.getCurrentTimeMillis();
        while (nextPacket != null && nextPacket.timestampMillis() <= currentTime) {
            channel.pipeline().fireChannelRead(Unpooled.wrappedBuffer(nextPacket.data()));
            try {
                nextPacket = packetReader.readNextPacket().orElse(null);
            } catch (IOException e) {
                nextPacket = null;
                break;
            }
        }
    }

    public synchronized void stopReplay() {
        if (!inReplay) {
            return;
        }
        inReplay = false;
        freecamController.setActive(false);

        if (connection != null) {
            try {
                connection.disconnect(Component.literal("Replay stopped"));
            } catch (Exception ignored) {
            }
            connection = null;
        }

        if (channel != null) {
            try {
                channel.close();
            } catch (Exception ignored) {
            }
            channel = null;
        }

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
        nextPacket = null;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.disconnect();
        }

        LOGGER.info("Recordly: Replay stopped cleanly");
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
