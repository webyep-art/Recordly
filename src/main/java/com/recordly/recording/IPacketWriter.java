package com.recordly.recording;

import java.io.Closeable;
import java.io.IOException;

public interface IPacketWriter extends Closeable {
    void writePacket(int timestampMillis, byte[] packetBytes) throws IOException;
    void flush() throws IOException;
}
