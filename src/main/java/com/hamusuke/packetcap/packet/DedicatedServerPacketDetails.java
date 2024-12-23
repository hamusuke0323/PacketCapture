package com.hamusuke.packetcap.packet;

import com.google.common.collect.ImmutableList;
import com.hamusuke.packetcap.utils.ByteConversion;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import net.minecraft.network.protocol.Packet;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class DedicatedServerPacketDetails extends PacketDetails implements DedicatedPacket {
    private final ImmutableList<String> hex;
    private final int size;
    private final String fSize;

    public DedicatedServerPacketDetails(Packet<?> packet, ByteBuf data) {
        super(packet);
        this.hex = ImmutableList.copyOf(ByteBufUtil.prettyHexDump(data).lines().toList());
        this.size = data.readableBytes();
        this.fSize = ByteConversion.convertBytes(this.size);
        data.release();
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
}
