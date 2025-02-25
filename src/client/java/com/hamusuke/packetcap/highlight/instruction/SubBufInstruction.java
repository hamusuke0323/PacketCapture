package com.hamusuke.packetcap.highlight.instruction;

import io.netty.buffer.ByteBuf;

public interface SubBufInstruction<B extends ByteBuf, V, T> extends BufInstruction<B, V> {
    T getField(V value);

    default String getDescription(T field) {
        return "";
    }
}
