package com.recordly.replay;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public class PacketStreamReader implements IPacketReader {
    private final DataInputStream dataInputStream;
    private boolean endReached = false;

    public PacketStreamReader(InputStream inputStream) {
        this.dataInputStream = new DataInputStream(new BufferedInputStream(inputStream, 65536));
    }

    @Override
    public Optional<PacketPayload> readNextPacket() throws IOException {
        if (endReached) {
            return Optional.empty();
        }
        try {
            int timestamp = dataInputStream.readInt();
            int length = dataInputStream.readInt();
            if (length < 0 || length > 33554432) {
                endReached = true;
                return Optional.empty();
            }
            byte[] buffer = new byte[length];
            dataInputStream.readFully(buffer);
            return Optional.of(new PacketPayload(timestamp, buffer));
        } catch (EOFException e) {
            endReached = true;
            return Optional.empty();
        }
    }

    @Override
    public boolean hasNext() {
        return !endReached;
    }

    @Override
    public void close() throws IOException {
        dataInputStream.close();
    }
}
