package com.hamusuke.packetcap.mixin;

import com.hamusuke.packetcap.invoker.PacketCodecDispatcherAccessor;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.network.handler.PacketCodecDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.Function;

@Mixin(PacketCodecDispatcher.class)
public abstract class PacketCodecDispatcherMixin<B extends ByteBuf, V, T> implements PacketCodecDispatcherAccessor<V, T> {
    @Shadow
    @Final
    private Object2IntMap<T> typeToIndex;

    @Shadow
    @Final
    private Function<V, ? extends T> packetIdGetter;

    @Override
    public Object2IntMap<T> getTypeToIndex() {
        return this.typeToIndex;
    }

    @Override
    public Function<V, ? extends T> getPacketIdGetter() {
        return this.packetIdGetter;
    }
}
