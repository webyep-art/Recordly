package com.recordly.replay;

public interface IReplayPlaybackController {
    void play();
    void pause();
    void togglePlayPause();
    boolean isPaused();
    int getCurrentTimeMillis();
    int getTotalDurationMillis();
    double getSpeed();
    void setSpeed(double speed);
    void update(long deltaRealMillis);
}
