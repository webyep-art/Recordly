package com.recordly.ui.overlay;

import com.recordly.recording.RecordingManager;
import com.recordly.replay.IReplayPlaybackController;
import com.recordly.replay.ReplayManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class ReplayControlOverlay {
    public static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) {
            return;
        }

        Font font = mc.font;
        RecordingManager recordingManager = RecordingManager.getInstance();
        if (recordingManager.isRecording() || recordingManager.isPaused()) {
            renderRecordingIndicator(graphics, font, recordingManager);
        }

        ReplayManager replayManager = ReplayManager.getInstance();
        if (replayManager.isInReplay()) {
            renderReplayControls(graphics, font, replayManager);
        }
    }

    private static void renderRecordingIndicator(GuiGraphics graphics, Font font, RecordingManager recordingManager) {
        int durationSec = recordingManager.getCurrentDurationMillis() / 1000;
        int minutes = durationSec / 60;
        int seconds = durationSec % 60;
        String text = String.format("REC %02d:%02d", minutes, seconds);

        int color = recordingManager.isPaused() ? 0xFFFFAA00 : 0xFFFF3333;
        graphics.fill(8, 8, 10 + font.width(text) + 14, 22, 0x80000000);
        graphics.fill(12, 12, 18, 18, color);
        graphics.drawString(font, text, 22, 11, 0xFFFFFF);
    }

    private static void renderReplayControls(GuiGraphics graphics, Font font, ReplayManager replayManager) {
        IReplayPlaybackController controller = replayManager.getPlaybackController();
        if (controller == null) {
            return;
        }

        int curSec = controller.getCurrentTimeMillis() / 1000;
        int totalSec = controller.getTotalDurationMillis() / 1000;
        String status = controller.isPaused() ? "PAUSED" : "PLAYING";
        String speed = String.format("%.1fx", controller.getSpeed());
        String text = String.format("[%s] %02d:%02d / %02d:%02d  (%s)  [Space: Pause, H: Speed]",
                status, curSec / 60, curSec % 60, totalSec / 60, totalSec % 60, speed);

        int screenWidth = graphics.guiWidth();
        int textWidth = font.width(text);
        int x = (screenWidth - textWidth) / 2;
        int y = 10;

        graphics.fill(x - 6, y - 4, x + textWidth + 6, y + 14, 0x90000000);
        graphics.drawString(font, text, x, y, 0x55FFFF);
    }
}
