package com.recordly.replay.camera;

import net.minecraft.world.phys.Vec3;

public interface ICameraController {
    void tick();
    void updateLook(double deltaX, double deltaY);
    Vec3 getPosition();
    void setPosition(Vec3 position);
    float getYaw();
    float getPitch();
    void setRotation(float yaw, float pitch);
    double getSpeed();
    void setSpeed(double speed);
    boolean isActive();
    void setActive(boolean active);
}
