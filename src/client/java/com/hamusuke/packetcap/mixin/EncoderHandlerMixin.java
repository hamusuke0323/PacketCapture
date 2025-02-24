package com.hamusuke.packetcap.mixin;

import com.hamusuke.packetcap.PacketCapture;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.NetworkState;
import net.minecraft.network.handler.EncoderHandler;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EncoderHandler.class)
public class EncoderHandlerMixin<T extends PacketListener> {
    @Shadow
    @Final
    private NetworkState<T> state;

    @Inject(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;Lio/netty/buffer/ByteBuf;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/codec/PacketCodec;encode(Ljava/lang/Object;Ljava/lang/Object;)V", shift = At.Shift.AFTER))
    private void encode(ChannelHandlerContext channelHandlerContext, Packet<T> packet, ByteBuf byteBuf, CallbackInfo ci) {
        PacketCapture.getInstance().onEncodingPacketInMultiplayer(this.state, packet, byteBuf);
    }
}
