package com.recordly.replay.network;

import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

public class ReplayConnection extends Connection {
    public ReplayConnection() {
        super(PacketFlow.CLIENTBOUND);
    }

    @Override
    public void send(Packet<?> packet, PacketSendListener listener, boolean flush) {
        if (listener != null) {
            try {
                listener.onSuccess();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void send(Packet<?> packet, PacketSendListener listener) {
        send(packet, listener, true);
    }

    @Override
    public void send(Packet<?> packet) {
        send(packet, null, true);
    }
}
