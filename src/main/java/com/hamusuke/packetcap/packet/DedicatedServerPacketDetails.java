package com.hamusuke.packetcap.packet;

import com.google.common.collect.ImmutableList;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import net.minecraft.network.protocol.Packet;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class DedicatedServerPacketDetails extends PacketDetails implements DedicatedPacket {
    private final ImmutableList<String> hex;
    private final int size;

    public DedicatedServerPacketDetails(Packet<?> packet, ByteBuf data) {
        super(packet);
        this.hex = ImmutableList.copyOf(ByteBufUtil.prettyHexDump(data).lines().toList());
        this.size = data.readableBytes();
    }

    @Override
    public int getSize() {
        return this.size;
    }

    @Override
    public ImmutableList<String> getHexLines() {
        return this.hex;
    }
}
