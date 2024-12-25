package com.hamusuke.packetcap.packet;

import com.google.common.collect.ImmutableList;
import com.hamusuke.packetcap.network.WrittenBytesLoggingByteBuf.WriteLog;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public interface DedicatedPacket {
    int getSize();

    String getFriendlySize();

    ImmutableList<String> getHexLines();

    int getPacketId();

    int getPacketIdEndIndex();

    List<WriteLog> getWriteLog();

    void createMap();

    Map<String, WriteLog> getMapForHighlighting();
}
