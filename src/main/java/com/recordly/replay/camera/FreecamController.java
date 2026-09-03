package com.recordly.replay.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class FreecamController implements ICameraController {
    private Vec3 position = Vec3.ZERO;
    private float yaw = 0.0f;
    private float pitch = 0.0f;
    private double speed = 1.0;
    private boolean active = false;

    private boolean forward = false;
    private boolean backward = false;
    private boolean left = false;
    private boolean right = false;
    private boolean up = false;
    private boolean down = false;
    private boolean sprint = false;

    @Override
    public void tick() {
        if (!active) {
            return;
        }

        double moveSpeed = speed * (sprint ? 2.5 : 1.0);
        double forwardInput = (forward ? 1.0 : 0.0) - (backward ? 1.0 : 0.0);
        double strafeInput = (left ? 1.0 : 0.0) - (right ? 1.0 : 0.0);
        double verticalInput = (up ? 1.0 : 0.0) - (down ? 1.0 : 0.0);

        float radYaw = yaw * (float) (Math.PI / 180.0);
        double sinYaw = Math.sin(radYaw);
        double cosYaw = Math.cos(radYaw);

        double motionX = (strafeInput * cosYaw - forwardInput * sinYaw) * moveSpeed;
        double motionZ = (forwardInput * cosYaw + strafeInput * sinYaw) * moveSpeed;
        double motionY = verticalInput * moveSpeed;

        position = position.add(motionX, motionY, motionZ);
    }

    @Override
    public void updateLook(double deltaX, double deltaY) {
        if (!active) {
            return;
        }
        double sensitivity = Minecraft.getInstance().options.sensitivity().get() * 0.6 + 0.2;
        double factor = sensitivity * sensitivity * sensitivity * 8.0;

        yaw += (float) (deltaX * factor * 0.15);
        pitch += (float) (deltaY * factor * 0.15);
        pitch = Mth.clamp(pitch, -90.0f, 90.0f);
    }

    public void setInputs(boolean forward, boolean backward, boolean left, boolean right, boolean up, boolean down, boolean sprint) {
        this.forward = forward;
        this.backward = backward;
        this.left = left;
        this.right = right;
        this.up = up;
        this.down = down;
        this.sprint = sprint;
    }

    @Override
    public Vec3 getPosition() {
        return position;
    }

    @Override
    public void setPosition(Vec3 position) {
        this.position = position;
    }

    @Override
    public float getYaw() {
        return yaw;
    }

    @Override
    public float getPitch() {
        return pitch;
    }

    @Override
    public void setRotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = Mth.clamp(pitch, -90.0f, 90.0f);
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
    public boolean isActive() {
        return active;
    }

    @Override
    public void setActive(boolean active) {
        this.active = active;
    }
}
