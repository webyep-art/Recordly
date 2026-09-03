package com.recordly.replay;

public class ReplayPlaybackController implements IReplayPlaybackController {
    private final int totalDurationMillis;
    private double currentTimeMillis = 0;
    private double speed = 1.0;
    private boolean paused = false;

    public ReplayPlaybackController(int totalDurationMillis) {
        this.totalDurationMillis = Math.max(0, totalDurationMillis);
    }

    @Override
    public void play() {
        this.paused = false;
    }

    @Override
    public void pause() {
        this.paused = true;
    }

    @Override
    public void togglePlayPause() {
        this.paused = !this.paused;
    }

    @Override
    public boolean isPaused() {
        return paused;
    }

    @Override
    public int getCurrentTimeMillis() {
        return (int) currentTimeMillis;
    }

    @Override
    public int getTotalDurationMillis() {
        return totalDurationMillis;
    }

    @Override
    public double getSpeed() {
        return speed;
    }

    @Override
    public void setSpeed(double speed) {
        this.speed = Math.max(0.1, Math.min(10.0, speed));
    }

    @Override
    public void update(long deltaRealMillis) {
        if (paused || deltaRealMillis <= 0) {
            return;
        }
        currentTimeMillis += deltaRealMillis * speed;
        if (currentTimeMillis > totalDurationMillis) {
            currentTimeMillis = totalDurationMillis;
            paused = true;
        }
    }
}
