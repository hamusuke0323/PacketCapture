package com.hamusuke.packetcap.mixin;

import com.hamusuke.packetcap.invoker.LoginKeyC2SPacketAccessor;
import net.minecraft.network.packet.c2s.login.LoginKeyC2SPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LoginKeyC2SPacket.class)
public abstract class LoginKeyC2SPacketMixin implements LoginKeyC2SPacketAccessor {
    @Shadow
    @Final
    private byte[] encryptedSecretKey;

    @Shadow
    @Final
    private byte[] nonce;

    @Override
    public byte[] getEncryptedSecretKey() {
        return this.encryptedSecretKey;
    }

    @Override
    public byte[] getNonce() {
        return this.nonce;
    }
}
