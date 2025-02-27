package com.hamusuke.packetcap.highlight.instruction;

import com.hamusuke.packetcap.highlight.Highlight;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface SameRefInstruction<B extends ByteBuf, T> extends BufInstruction<B, T> {
    @Override
    default List<Highlight<?>> write(int curWriterIndex, @Nullable B receivedByteBuf, B buf, T value) {
        return List.of();
    }

    int getHighlightIndex();
}
