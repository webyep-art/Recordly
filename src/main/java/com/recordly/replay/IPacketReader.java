package com.recordly.replay;

import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;

public interface IPacketReader extends Closeable {
    Optional<PacketPayload> readNextPacket() throws IOException;
    boolean hasNext();
}
