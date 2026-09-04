package com.recordly.client;

import com.recordly.Recordly;
import com.recordly.recording.RecordingManager;
import com.recordly.recording.network.RecordlyPacketChannelHandler;
import com.recordly.replay.IReplayPlaybackController;
import com.recordly.replay.ReplayManager;
import com.recordly.replay.camera.FreecamController;
import com.recordly.ui.overlay.ReplayControlOverlay;
import com.recordly.ui.screen.ReplayListScreen;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

public class RecordlyClientEvents {
    private static final RecordlyPacketChannelHandler CHANNEL_HANDLER = new RecordlyPacketChannelHandler();

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (ReplayManager.getInstance().isInReplay()) {
            return;
        }

        Connection connection = event.getConnection();
        if (connection != null) {
            Channel channel = connection.channel();
            if (channel != null && channel.pipeline() != null) {
                ChannelPipeline pipeline = channel.pipeline();
                if (pipeline.get(RecordlyPacketChannelHandler.HANDLER_NAME) == null) {
                    if (pipeline.get("decoder") != null) {
                        pipeline.addBefore("decoder", RecordlyPacketChannelHandler.HANDLER_NAME, CHANNEL_HANDLER);
                    } else {
                        pipeline.addFirst(RecordlyPacketChannelHandler.HANDLER_NAME, CHANNEL_HANDLER);
                    }
                }
            }
        }

        Minecraft mc = Minecraft.getInstance();
        boolean isSingleplayer = mc.isSingleplayer();
        String sessionName = isSingleplayer ? "Singleplayer" : (mc.getCurrentServer() != null ? mc.getCurrentServer().name : "Multiplayer");
        if (RecordingManager.getInstance().isAutoRecordEnabled()) {
            RecordingManager.getInstance().startRecording(sessionName, isSingleplayer);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        RecordingManager.getInstance().stopRecording();
        ReplayManager.getInstance().stopReplay();
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            return;
        }

        ReplayManager replayManager = ReplayManager.getInstance();
        if (!replayManager.isInReplay()) {
            return;
        }

        IReplayPlaybackController controller = replayManager.getPlaybackController();
        FreecamController freecam = replayManager.getFreecamController();

        if (event.getKey() == GLFW.GLFW_KEY_P) {
            if (controller != null) {
                controller.togglePlayPause();
            }
        } else if (event.getKey() == GLFW.GLFW_KEY_F6 || event.getKey() == GLFW.GLFW_KEY_V) {
            freecam.setActive(!freecam.isActive());
        } else if (event.getKey() == GLFW.GLFW_KEY_RIGHT_BRACKET) {
            if (controller != null) {
                controller.setSpeed(controller.getSpeed() + 0.25);
            }
        } else if (event.getKey() == GLFW.GLFW_KEY_LEFT_BRACKET) {
            if (controller != null) {
                controller.setSpeed(controller.getSpeed() - 0.25);
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ReplayManager replayManager = ReplayManager.getInstance();

        if (replayManager.isInReplay()) {
            replayManager.tickPlayback();

            if (mc.player != null) {
                if (!mc.player.getAbilities().flying) {
                    mc.player.getAbilities().flying = true;
                    mc.player.getAbilities().mayfly = true;
                    mc.player.onUpdateAbilities();
                }
                mc.player.noPhysics = true;

                FreecamController freecam = replayManager.getFreecamController();
                freecam.setPosition(mc.player.position());
                freecam.setRotation(mc.player.getYRot(), mc.player.getXRot());

                if (mc.level != null) {
                    int chunkX = ((int) Math.floor(mc.player.getX())) >> 4;
                    int chunkZ = ((int) Math.floor(mc.player.getZ())) >> 4;
                    mc.level.getChunkSource().updateViewCenter(chunkX, chunkZ);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        ReplayControlOverlay.render(event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof TitleScreen titleScreen) {
            int buttonWidth = 98;
            int buttonHeight = 20;
            int x = titleScreen.width / 2 + 104;
            int y = titleScreen.height / 4 + 48;

            event.addListener(Button.builder(Component.literal("Replays"), b -> {
                Minecraft.getInstance().setScreen(new ReplayListScreen(titleScreen, Recordly.getStorage()));
            }).bounds(x, y, buttonWidth, buttonHeight).build());
        }
    }
}
