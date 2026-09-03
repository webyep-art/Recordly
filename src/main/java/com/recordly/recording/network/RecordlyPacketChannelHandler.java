package com.recordly.recording.network;

import com.recordly.recording.RecordingManager;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

@ChannelHandler.Sharable
public class RecordlyPacketChannelHandler extends ChannelInboundHandlerAdapter {
    public static final String HANDLER_NAME = "recordly_packet_capture";

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf buf) {
            if (buf.isReadable()) {
                int length = buf.readableBytes();
                byte[] packetData = new byte[length];
                buf.getBytes(buf.readerIndex(), packetData);
                RecordingManager.getInstance().recordPacket(packetData);
            }
        }
        super.channelRead(ctx, msg);
    }
}
