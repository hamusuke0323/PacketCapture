package com.hamusuke.packetcap.highlight.instruction;

import com.hamusuke.packetcap.highlight.Highlight;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.hamusuke.packetcap.highlight.Highlight.getWrittenByteLen;

public class ConstantInstruction<B extends ByteBuf, T, V> implements BufInstruction<B, V> {
    private final BufInstruction<B, T> constantInstruction;

    public ConstantInstruction(BufInstruction<B, T> constantInstruction) {
        this.constantInstruction = constantInstruction;
    }

    @Override
    public List<Highlight<?>> write(int curWriterIndex, @Nullable B receivedByteBuf, B buf, V value) {
        var h = this.constantInstruction.write(curWriterIndex, receivedByteBuf, buf, null);
        if (receivedByteBuf != null) {
            receivedByteBuf.readerIndex(curWriterIndex + getWrittenByteLen(h));
        }

        return h;
    }
}
