package com.hamusuke.packetcap.packet;

import com.google.common.collect.ImmutableList;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface DedicatedPacket {
    int getSize();

    String getFriendlySize();

    ImmutableList<String> getHexLines();
}
