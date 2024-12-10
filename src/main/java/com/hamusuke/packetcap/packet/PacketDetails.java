package com.hamusuke.packetcap.packet;

import com.hamusuke.packetcap.clazz.visitor.ClassVisitor;
import net.minecraft.network.protocol.Packet;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PacketDetails {
    private final ClassVisitor visitor;

    public PacketDetails(Packet<?> packet) {
        this.visitor = new ClassVisitor(packet.getClass(), packet);
    }

    public String getPacketClassName() {
        return this.visitor.getClassName();
    }

    public ClassVisitor getVisitor() {
        return this.visitor;
    }
}
