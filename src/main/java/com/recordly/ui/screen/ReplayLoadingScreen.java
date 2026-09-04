package com.recordly.ui.screen;

import com.recordly.replay.ReplayManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class ReplayLoadingScreen extends Screen {
    private final Screen parentScreen;
    private final String replayName;
    private int ticksElapsed = 0;

    public ReplayLoadingScreen(Screen parentScreen, String replayName) {
        super(Component.literal("Loading Replay"));
        this.parentScreen = parentScreen;
        this.replayName = replayName;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> {
            ReplayManager.getInstance().stopReplay();
            this.minecraft.setScreen(parentScreen);
        }).bounds(this.width / 2 - 100, this.height / 2 + 40, 200, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        ticksElapsed++;

        ReplayManager replayManager = ReplayManager.getInstance();
        if (!replayManager.isInReplay()) {
            this.minecraft.setScreen(parentScreen);
            return;
        }

        replayManager.pumpInitialPackets();

        if (this.minecraft.level != null && this.minecraft.player != null) {
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, Component.literal("Loading Replay: " + replayName), this.width / 2, this.height / 2 - 30, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal("Reconstructing world from packets... (" + ticksElapsed + ")"), this.width / 2, this.height / 2 - 10, 0xAAAAAA);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        ReplayManager.getInstance().stopReplay();
        super.onClose();
    }
}
