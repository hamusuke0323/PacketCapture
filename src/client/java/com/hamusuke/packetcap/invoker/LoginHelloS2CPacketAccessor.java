package com.hamusuke.packetcap.invoker;

import net.minecraft.network.packet.s2c.login.LoginHelloS2CPacket;

public interface LoginHelloS2CPacketAccessor {
    static byte[] getPublicKeyBytes(LoginHelloS2CPacket packet) {
        return ((LoginHelloS2CPacketAccessor) packet).getPublicKeyBytes();
    }

    byte[] getPublicKeyBytes();
}
