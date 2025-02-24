package com.hamusuke.packetcap.mixin;

import com.hamusuke.packetcap.invoker.LoginHelloS2CPacketAccessor;
import net.minecraft.network.packet.s2c.login.LoginHelloS2CPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LoginHelloS2CPacket.class)
public abstract class LoginHelloS2CPacketMixin implements LoginHelloS2CPacketAccessor {
    @Shadow
    @Final
    private byte[] publicKey;

    @Override
    public byte[] getPublicKeyBytes() {
        return this.publicKey;
    }
}
