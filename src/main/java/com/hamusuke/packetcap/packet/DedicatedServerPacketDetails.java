package com.hamusuke.packetcap.packet;

import com.google.common.collect.ImmutableList;
import com.hamusuke.packetcap.network.WrittenBytesLoggingByteBuf;
import com.hamusuke.packetcap.network.WrittenBytesLoggingByteBuf.WriteLog;
import com.hamusuke.packetcap.utils.ByteConversion;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import net.minecraft.network.protocol.Packet;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class DedicatedServerPacketDetails extends PacketDetails implements DedicatedPacket {
    private final ImmutableList<String> hex;
    private final int size;
    private final String fSize;
    private final int packetId;
    private final int packetIdEndIndex;
    private final List<WriteLog> writeLog;

    public DedicatedServerPacketDetails(Packet<?> packet, ByteBuf data, WrittenBytesLoggingByteBuf buf) {
        super(packet);
        this.hex = ImmutableList.copyOf(ByteBufUtil.prettyHexDump(data).lines().toList());
        this.size = data.readableBytes();
        this.fSize = ByteConversion.convertBytes(this.size);
        data.release();

        this.packetId = buf.getPacketId();
        this.packetIdEndIndex = buf.getPacketIdEndIndex();
        this.writeLog = buf.getLogs();
    }

    @Override
    public int getSize() {
        return this.size;
    }

    @Override
    public String getFriendlySize() {
        return this.fSize;
    }

    @Override
    public ImmutableList<String> getHexLines() {
        return this.hex;
    }

    @Override
    public int getPacketId() {
        return this.packetId;
    }

    @Override
    public int getPacketIdEndIndex() {
        return this.packetIdEndIndex;
    }

    @Override
    public List<WriteLog> getWriteLog() {
        return this.writeLog;
    }
}
