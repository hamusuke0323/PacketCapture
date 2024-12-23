package com.hamusuke.packetcap.mixin;

import com.hamusuke.packetcap.PacketCapture;
import com.hamusuke.packetcap.clazz.visitor.ClassVisitor;
import com.hamusuke.packetcap.network.WrittenBytesLoggingByteBuf;
import com.hamusuke.packetcap.packet.DedicatedServerPacketDetails;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.PacketListener;
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
public class PacketDecoderMixin<T extends PacketListener> {
    @Shadow
    @Final
    private ProtocolInfo<T> protocolInfo;

    @Inject(method = "decode", at = @At("HEAD"))
    private void decode(ChannelHandlerContext context, ByteBuf buf, List<Object> out, CallbackInfo ci) {
        var capture = PacketCapture.getInstance();
        if (!capture.isCapturing() || Minecraft.getInstance().hasSingleplayerServer()) {
            return;
        }

        var byteBuf = buf.copy();
        int i = byteBuf.readableBytes();
        if (i == 0) {
            byteBuf.release();
            return;
        }

        var delivered = byteBuf.copy();
        var packet = this.protocolInfo.codec().decode(byteBuf);
        if (packet.type().flow() == PacketFlow.SERVERBOUND || byteBuf.readableBytes() > 0) {
            byteBuf.release();
            delivered.release();
            return;
        }

        byteBuf.release();

        var newBuf = new WrittenBytesLoggingByteBuf(Unpooled.buffer());
        this.protocolInfo.codec().encode(newBuf, packet);
        newBuf.onFinishWriting();
        newBuf.release();

        capture.addToReceived(new DedicatedServerPacketDetails(packet, delivered, newBuf));
    }
}
