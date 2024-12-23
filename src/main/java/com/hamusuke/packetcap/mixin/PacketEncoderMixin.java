package com.hamusuke.packetcap.mixin;

import com.hamusuke.packetcap.PacketCapture;
import com.hamusuke.packetcap.clazz.visitor.ClassVisitor;
import com.hamusuke.packetcap.network.WrittenBytesLoggingByteBuf;
import com.hamusuke.packetcap.packet.DedicatedServerPacketDetails;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PacketEncoder.class)
public class PacketEncoderMixin<T extends PacketListener> {
    @Shadow
    @Final
    private ProtocolInfo<T> protocolInfo;

    @Inject(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Lio/netty/buffer/ByteBuf;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/codec/StreamCodec;encode(Ljava/lang/Object;Ljava/lang/Object;)V", shift = At.Shift.AFTER))
    private void encode(ChannelHandlerContext context, Packet<T> packet, ByteBuf byteBuf, CallbackInfo ci) {
        var capture = PacketCapture.getInstance();
        if (Minecraft.getInstance().hasSingleplayerServer() || packet.type().flow() == PacketFlow.CLIENTBOUND || !capture.isCapturing()) {
            return;
        }

        var newBuf = new WrittenBytesLoggingByteBuf(Unpooled.buffer());
        this.protocolInfo.codec().encode(newBuf, packet);
        newBuf.onFinishWriting();
        newBuf.release();

        capture.addToSent(new DedicatedServerPacketDetails(packet, byteBuf.copy(), newBuf));
    }
}
