package com.hamusuke.packetcap.mixin;

import com.hamusuke.packetcap.PacketCapture;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.NetworkState;
import net.minecraft.network.handler.DecoderHandler;
import net.minecraft.network.listener.PacketListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(DecoderHandler.class)
public class DecoderHandlerMixin<T extends PacketListener> {
    @Shadow
    @Final
    private NetworkState<T> state;

    @Inject(method = "decode", at = @At("HEAD"))
    private void decode(ChannelHandlerContext context, ByteBuf buf, List<Object> out, CallbackInfo ci) {
        PacketCapture.getInstance().onDecodingPacketInMultiplayer(this.state, buf);
    }
}
