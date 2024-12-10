package com.hamusuke.packetcap.mixin;

import com.hamusuke.packetcap.PacketCapture;
import com.hamusuke.packetcap.packet.DedicatedServerPacketDetails;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(PacketDecoder.class)
public class PacketDecoderMixin {
    @Shadow
    @Final
    private ProtocolInfo<?> protocolInfo;

    @Inject(method = "decode", at = @At("HEAD"))
    private void decode(ChannelHandlerContext context, ByteBuf buf, List<Object> out, CallbackInfo ci) {
        var capture = PacketCapture.getInstance();
        if (!capture.isCapturing()) {
            return;
        }

        var byteBuf = buf.copy();
        int i = byteBuf.readableBytes();
        if (i == 0) {
            return;
        }

        var packet = this.protocolInfo.codec().decode(byteBuf);
        if (Minecraft.getInstance().hasSingleplayerServer() || packet.type().flow() == PacketFlow.SERVERBOUND || byteBuf.readableBytes() > 0) {
            return;
        }

        capture.addToReceived(new DedicatedServerPacketDetails(packet, buf.copy()));
    }
}
