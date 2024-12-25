package com.hamusuke.packetcap.mixin;

import com.hamusuke.packetcap.PacketCapture;
import com.hamusuke.packetcap.packet.PacketDetails;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Shadow
    private Channel channel;

    @Shadow
    @Final
    private PacketFlow receiving;

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
    private void receive(ChannelHandlerContext p_129487_, Packet<?> p_129488_, CallbackInfo ci) {
        var capture = PacketCapture.getInstance();

        if (this.isClientSide(capture) && this.channel.isOpen()) {
            capture.addToReceived(new PacketDetails(p_129488_));
        }
    }

    @Inject(method = "sendPacket", at = @At("HEAD"))
    private void send(Packet<?> p_129521_, PacketSendListener p_243246_, boolean p_299777_, CallbackInfo ci) {
        var capture = PacketCapture.getInstance();

        if (this.isClientSide(capture)) {
            capture.addToSent(new PacketDetails(p_129521_));
        }
    }

    @Unique
    private boolean isClientSide(PacketCapture capture) {
        return this.receiving == PacketFlow.CLIENTBOUND && capture.isCapturing() && Minecraft.getInstance().hasSingleplayerServer();
    }
}
