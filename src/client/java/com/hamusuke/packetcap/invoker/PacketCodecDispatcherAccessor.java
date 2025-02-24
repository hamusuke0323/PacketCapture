package com.hamusuke.packetcap.invoker;

import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.util.function.Function;

public interface PacketCodecDispatcherAccessor<V, T> {
    Object2IntMap<T> getTypeToIndex();

    Function<V, ? extends T> getPacketIdGetter();
}
