package com.hamusuke.packetcap.highlight.instruction;

import com.hamusuke.packetcap.highlight.Highlight;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface RestartInstruction extends BufInstruction {
    RestartInstruction INSTANCE = new RestartInstruction() {
    };

    @Override
    default List<Highlight<?>> write(int curWriterIndex, @Nullable ByteBuf receivedByteBuf, ByteBuf buf, Object value) {
        throw new RuntimeException("Do not invoke this function! This instruction just indicates highlighter to restart writing.");
    }
}
