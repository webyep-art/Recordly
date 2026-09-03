package com.recordly.replay;

public record PacketPayload(int timestampMillis, byte[] data) {
}
