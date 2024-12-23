package com.hamusuke.packetcap.packet;

import com.google.common.collect.ImmutableList;
import com.hamusuke.packetcap.network.WrittenBytesLoggingByteBuf.WriteLog;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public interface DedicatedPacket {
    int getSize();

    String getFriendlySize();

    ImmutableList<String> getHexLines();

    int getPacketId();

    int getPacketIdEndIndex();

    List<WriteLog> getWriteLog();
}
