package com.recordly.recording;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class PacketStreamWriter implements IPacketWriter {
    private final DataOutputStream dataOutputStream;

    public PacketStreamWriter(OutputStream outputStream) {
        this.dataOutputStream = new DataOutputStream(new BufferedOutputStream(outputStream, 65536));
    }

    @Override
    public synchronized void writePacket(int timestampMillis, byte[] packetBytes) throws IOException {
        dataOutputStream.writeInt(timestampMillis);
        dataOutputStream.writeInt(packetBytes.length);
        dataOutputStream.write(packetBytes);
    }

    @Override
    public synchronized void flush() throws IOException {
        dataOutputStream.flush();
    }

    @Override
    public synchronized void close() throws IOException {
        dataOutputStream.close();
    }
}
