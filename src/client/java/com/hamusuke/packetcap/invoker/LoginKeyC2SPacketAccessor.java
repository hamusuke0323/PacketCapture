package com.hamusuke.packetcap.invoker;

import net.minecraft.network.packet.c2s.login.LoginKeyC2SPacket;

public interface LoginKeyC2SPacketAccessor {
    static byte[] getEncryptedSecretKey(LoginKeyC2SPacket packet) {
        return ((LoginKeyC2SPacketAccessor) packet).getEncryptedSecretKey();
    }

    static byte[] getNonce(LoginKeyC2SPacket packet) {
        return ((LoginKeyC2SPacketAccessor) packet).getNonce();
    }

    byte[] getEncryptedSecretKey();

    byte[] getNonce();
}
