package com.recordly.ui.screen;

import com.recordly.Recordly;
import com.recordly.replay.ReplayManager;
import com.recordly.storage.RecordlyFile;
import com.recordly.storage.RecordlyMetadata;
import com.recordly.storage.RecordlyStorage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public class ReplayListScreen extends Screen {
    private final Screen parentScreen;
    private final RecordlyStorage storage;
    private ReplaySelectionList selectionList;
    private Button playButton;
    private Button deleteButton;

    public ReplayListScreen(Screen parentScreen, RecordlyStorage storage) {
        super(Component.literal("Recordly - Replays"));
        this.parentScreen = parentScreen;
        this.storage = storage;
    }

    @Override
    protected void init() {
        this.selectionList = new ReplaySelectionList(this.minecraft, this.width, this.height - 96, 32, 36);
        this.addRenderableWidget(this.selectionList);

        this.playButton = Button.builder(Component.literal("Play Replay"), b -> playSelected())
                .bounds(this.width / 2 - 154, this.height - 56, 150, 20)
                .build();
        this.addRenderableWidget(this.playButton);

        this.deleteButton = Button.builder(Component.literal("Delete"), b -> deleteSelected())
                .bounds(this.width / 2 + 4, this.height - 56, 150, 20)
                .build();
        this.addRenderableWidget(this.deleteButton);

        this.addRenderableWidget(Button.builder(Component.literal("Open Folder"), b -> Util.getPlatform().openPath(storage.getStorageDirectory()))
                .bounds(this.width / 2 - 154, this.height - 28, 150, 20)
                .build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> this.minecraft.setScreen(this.parentScreen))
                .bounds(this.width / 2 + 4, this.height - 28, 150, 20)
                .build());

        updateButtonStates();
        reloadReplays();
    }

    private void reloadReplays() {
        selectionList.clearEntries();
        List<RecordlyFile> files = storage.listRecordings();
        for (RecordlyFile file : files) {
            selectionList.addEntry(new ReplayEntry(file));
        }
        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean hasSelection = selectionList != null && selectionList.getSelected() != null;
        if (playButton != null) {
            playButton.active = hasSelection;
        }
        if (deleteButton != null) {
            deleteButton.active = hasSelection;
        }
    }

    private void playSelected() {
        ReplayEntry selected = selectionList.getSelected();
        if (selected == null) {
            return;
        }
        boolean started = ReplayManager.getInstance().startReplay(selected.file);
        if (started) {
            this.minecraft.setScreen(null);
        }
    }

    private void deleteSelected() {
        ReplayEntry selected = selectionList.getSelected();
        if (selected == null) {
            return;
        }
        this.minecraft.setScreen(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        selected.file.delete();
                        reloadReplays();
                    }
                    this.minecraft.setScreen(ReplayListScreen.this);
                },
                Component.literal("Delete Replay"),
                Component.literal("Are you sure you want to delete '" + selected.file.getName() + "'?")
        ));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
    }

    public class ReplaySelectionList extends ObjectSelectionList<ReplayEntry> {
        public ReplaySelectionList(Minecraft minecraft, int width, int height, int y0, int itemHeight) {
            super(minecraft, width, height, y0, itemHeight);
        }

        @Override
        public void setSelected(ReplayEntry entry) {
            super.setSelected(entry);
            updateButtonStates();
        }
    }

    public class ReplayEntry extends ObjectSelectionList.Entry<ReplayEntry> {
        private final RecordlyFile file;
        private final RecordlyMetadata metadata;

        public ReplayEntry(RecordlyFile file) {
            this.file = file;
            this.metadata = file.readMetadata().orElse(null);
        }

        @Override
        public Component getNarration() {
            return Component.literal(file.getName());
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            String title = file.getName();
            graphics.drawString(font, title, left + 4, top + 2, 0xFFFFFF);

            String info;
            if (metadata != null) {
                int durationSeconds = metadata.getDuration() / 1000;
                int minutes = durationSeconds / 60;
                int seconds = durationSeconds % 60;
                String timeFormatted = String.format("%02d:%02d", minutes, seconds);
                String dateFormatted = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(metadata.getDate()));
                info = metadata.getServerName() + " | " + timeFormatted + " | " + dateFormatted;
            } else {
                info = "Unknown Metadata";
            }
            graphics.drawString(font, info, left + 4, top + 16, 0x888888);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            selectionList.setSelected(this);
            return true;
        }
    }
}
