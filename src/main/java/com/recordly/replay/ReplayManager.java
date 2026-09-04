package com.recordly.replay;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import com.recordly.replay.camera.FreecamController;
import com.recordly.replay.network.DropOutboundHandler;
import com.recordly.replay.network.ReplayConnection;
import com.recordly.storage.RecordlyFile;
import com.recordly.storage.RecordlyMetadata;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.telemetry.WorldSessionTelemetryManager;
import net.minecraft.core.Holder;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ServerLinks;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForgeConfig;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import net.neoforged.neoforge.network.registration.NetworkChannel;
import net.neoforged.neoforge.network.registration.NetworkPayloadSetup;
import net.neoforged.neoforge.registries.DataPackRegistriesHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class ReplayManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReplayManager.class);
    private static final ReplayManager INSTANCE = new ReplayManager();

    private final FreecamController freecamController = new FreecamController();
    private RegistryAccess.Frozen cachedRegistries;
    private RecordlyFile currentReplayFile;
    private RecordlyMetadata currentMetadata;
    private IReplayPlaybackController playbackController;
    private IPacketReader packetReader;
    private PacketPayload nextPacket;
    private EmbeddedChannel channel;
    private Connection connection;
    private ClientPacketListener packetListener;
    private boolean inReplay = false;

    private ReplayManager() {
    }

    public static ReplayManager getInstance() {
        return INSTANCE;
    }

    private ChunkPos findInitialChunkPos(RecordlyFile file) {
        try {
            Optional<InputStream> streamOpt = file.openPacketStream();
            if (streamOpt.isEmpty()) {
                return new ChunkPos(0, 0);
            }
            try (InputStream is = streamOpt.get()) {
                PacketStreamReader reader = new PacketStreamReader(is);
                for (int i = 0; i < 500 && reader.hasNext(); i++) {
                    Optional<PacketPayload> opt = reader.readNextPacket();
                    if (opt.isEmpty()) {
                        break;
                    }
                    byte[] data = opt.get().data();
                    if (data.length >= 9) {
                        int id = data[0] & 0x7F;
                        int offset = 1;
                        if ((data[0] & 0x80) != 0 && data.length >= 10) {
                            id |= (data[1] & 0x7F) << 7;
                            offset = 2;
                        }
                        if (id == 0x27 && data.length >= offset + 8) {
                            ByteBuffer bb = ByteBuffer.wrap(data, offset, 8);
                            int cx = bb.getInt();
                            int cz = bb.getInt();
                            return new ChunkPos(cx, cz);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return new ChunkPos(0, 0);
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

            this.connection = new ReplayConnection();
            this.channel.pipeline().addLast("packet_handler", connection);
            this.channel.pipeline().fireChannelActive();

            Map<ResourceLocation, NetworkChannel> anyChannelMap = new HashMap<>() {
                @Override
                public NetworkChannel get(Object key) {
                    if (key instanceof ResourceLocation loc) {
                        return new NetworkChannel(loc, "1");
                    }
                    return null;
                }

                @Override
                public boolean containsKey(Object key) {
                    return true;
                }
            };
            NetworkPayloadSetup payloadSetup = new NetworkPayloadSetup(Map.of(
                    ConnectionProtocol.PLAY, anyChannelMap,
                    ConnectionProtocol.CONFIGURATION, anyChannelMap
            ));
            ChannelAttributes.setPayloadSetup(connection, payloadSetup);
            ChannelAttributes.setConnectionType(connection, ConnectionType.NEOFORGE);

            if (this.cachedRegistries == null) {
                this.cachedRegistries = loadRegistries(mc);
            }
            RegistryAccess.Frozen registries = this.cachedRegistries;
            ensureServerConfigCached();
            GameProfile profile = new GameProfile(UUID.randomUUID(), "ReplayViewer");
            WorldSessionTelemetryManager telemetry = mc.getTelemetryManager().createWorldSessionManager(false, null, "replay");

            CommonListenerCookie cookie = new CommonListenerCookie(
                    profile,
                    telemetry,
                    registries,
                    FeatureFlags.DEFAULT_FLAGS,
                    "Recordly",
                    null,
                    parentScreen,
                    Collections.emptyMap(),
                    null,
                    false,
                    Collections.emptyMap(),
                    ServerLinks.EMPTY,
                    ConnectionType.NEOFORGE
            );

            this.packetListener = new ClientPacketListener(mc, connection, cookie);

            Holder<DimensionType> dimensionType = registries.registryOrThrow(Registries.DIMENSION_TYPE)
                    .getHolderOrThrow(BuiltinDimensionTypes.OVERWORLD);
            CommonPlayerSpawnInfo spawnInfo = new CommonPlayerSpawnInfo(
                    dimensionType,
                    Level.OVERWORLD,
                    0L,
                    GameType.SPECTATOR,
                    null,
                    false,
                    false,
                    null,
                    0
            );

            ChunkPos initialChunk = findInitialChunkPos(file);

            ClientboundLoginPacket loginPacket = new ClientboundLoginPacket(
                    1,
                    false,
                    Set.of(Level.OVERWORLD),
                    1,
                    32,
                    32,
                    false,
                    true,
                    false,
                    spawnInfo,
                    false
            );

            this.connection.setupInboundProtocol(GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(registries)), packetListener);
            this.connection.setupOutboundProtocol(GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(registries)));

            this.inReplay = true;
            this.packetListener.handleLogin(loginPacket);
            this.packetListener.handleSetChunkCacheCenter(new ClientboundSetChunkCacheCenterPacket(initialChunk.x, initialChunk.z));
            this.packetListener.handleSetChunkCacheRadius(new ClientboundSetChunkCacheRadiusPacket(32));

            Abilities abilities = new Abilities();
            abilities.flying = true;
            abilities.mayfly = true;
            abilities.invulnerable = true;
            abilities.setFlyingSpeed(0.08f);
            this.packetListener.handlePlayerAbilities(new ClientboundPlayerAbilitiesPacket(abilities));

            double spawnX = (initialChunk.x << 4) + 8.0;
            double spawnZ = (initialChunk.z << 4) + 8.0;
            double spawnY = 80.0;
            this.packetListener.handleMovePlayer(new ClientboundPlayerPositionPacket(spawnX, spawnY, spawnZ, 0.0f, 0.0f, Collections.emptySet(), 0));

            mc.setScreen(null);

            if (mc.level != null) {
                mc.level.getChunkSource().updateViewRadius(32);
                mc.level.getChunkSource().updateViewCenter(initialChunk.x, initialChunk.z);
            }

            if (mc.player != null) {
                mc.player.setPos(spawnX, spawnY, spawnZ);
                mc.player.xo = spawnX;
                mc.player.yo = spawnY;
                mc.player.zo = spawnZ;
                mc.player.setDeltaMovement(Vec3.ZERO);
                mc.player.getAbilities().flying = true;
                mc.player.getAbilities().mayfly = true;
                mc.player.getAbilities().invulnerable = true;
                mc.player.getAbilities().setFlyingSpeed(0.08f);
                mc.player.onUpdateAbilities();
                mc.player.noPhysics = true;

                freecamController.setActive(true);
                freecamController.setPosition(new Vec3(spawnX, spawnY, spawnZ));
                freecamController.setRotation(0.0f, 0.0f);
            }

            while (nextPacket != null && nextPacket.timestampMillis() <= 50) {
                channel.pipeline().fireChannelRead(Unpooled.wrappedBuffer(nextPacket.data()));
                try {
                    nextPacket = packetReader.readNextPacket().orElse(null);
                } catch (IOException e) {
                    nextPacket = null;
                    break;
                }
            }
            connection.tick();
            channel.runPendingTasks();

            LOGGER.info("Recordly: Replay world created for '{}'", file.getName());
            return true;
        } catch (Exception e) {
            LOGGER.error("Recordly: Failed to open replay", e);
            stopReplay();
            return false;
        }
    }

    public synchronized void tickPlayback() {
        if (!inReplay || channel == null || connection == null || playbackController == null) {
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

        try {
            connection.tick();
            channel.runPendingTasks();
        } catch (Exception ignored) {
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
                if (connection instanceof ReplayConnection replayConnection) {
                    replayConnection.setAllowDisconnect(true);
                }
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
        packetListener = null;
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

    private RegistryAccess.Frozen loadRegistries(Minecraft mc) throws Exception {
        PackRepository packRepository = mc.getResourcePackRepository();
        packRepository.reload();
        WorldLoader.PackConfig packConfig = new WorldLoader.PackConfig(
                packRepository,
                WorldDataConfiguration.DEFAULT,
                false,
                false
        );
        Pair<WorldDataConfiguration, CloseableResourceManager> pair = packConfig.createResourceManager();
        try (CloseableResourceManager resourceManager = pair.getSecond()) {
            LayeredRegistryAccess<RegistryLayer> layeredAccess = RegistryLayer.createRegistryAccess();
            RegistryAccess.Frozen worldgenAccess = RegistryDataLoader.load(
                    resourceManager,
                    layeredAccess.getAccessForLoading(RegistryLayer.WORLDGEN),
                    DataPackRegistriesHooks.getDataPackRegistries()
            );
            layeredAccess = layeredAccess.replaceFrom(RegistryLayer.WORLDGEN, worldgenAccess);

            RegistryAccess.Frozen dimensionAccess = RegistryDataLoader.load(
                    resourceManager,
                    layeredAccess.getAccessForLoading(RegistryLayer.DIMENSIONS),
                    RegistryDataLoader.DIMENSION_REGISTRIES
            );
            layeredAccess = layeredAccess.replaceFrom(RegistryLayer.DIMENSIONS, dimensionAccess);

            return layeredAccess.compositeAccess();
        }
    }

    private void ensureServerConfigCached() {
        try {
            Field cachedField = ModConfigSpec.ConfigValue.class.getDeclaredField("cachedValue");
            cachedField.setAccessible(true);
            cachedField.set(NeoForgeConfig.SERVER.removeErroringEntities, Boolean.FALSE);
            cachedField.set(NeoForgeConfig.SERVER.removeErroringBlockEntities, Boolean.FALSE);
            cachedField.set(NeoForgeConfig.SERVER.fullBoundingBoxLadders, Boolean.FALSE);
            cachedField.set(NeoForgeConfig.SERVER.permissionHandler, "default");
            cachedField.set(NeoForgeConfig.SERVER.advertiseDedicatedServerToLan, Boolean.FALSE);
        } catch (Exception ignored) {
        }
    }
}
