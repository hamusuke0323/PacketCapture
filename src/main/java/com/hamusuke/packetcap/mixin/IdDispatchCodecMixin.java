package com.hamusuke.packetcap.mixin;

import com.hamusuke.packetcap.network.WrittenBytesLoggingByteBuf;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.network.codec.IdDispatchCodec;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

@Mixin(IdDispatchCodec.class)
public abstract class IdDispatchCodecMixin<B extends ByteBuf, V, T> {
    @Shadow
    @Final
    private Function<V, ? extends T> typeGetter;

    @Shadow
    @Final
    private Object2IntMap<T> toId;

    @Inject(method = "encode(Lio/netty/buffer/ByteBuf;Ljava/lang/Object;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/VarInt;write(Lio/netty/buffer/ByteBuf;I)Lio/netty/buffer/ByteBuf;", shift = Shift.AFTER))
    private void encode(B p_336072_, V p_327912_, CallbackInfo ci) {
        if (p_336072_ instanceof WrittenBytesLoggingByteBuf buf) {
            int packetId = this.toId.getOrDefault(this.typeGetter.apply(p_327912_), -1);
            if (packetId != -1) {
                buf.assignPacketId(packetId);
            }
        }
    }
}
