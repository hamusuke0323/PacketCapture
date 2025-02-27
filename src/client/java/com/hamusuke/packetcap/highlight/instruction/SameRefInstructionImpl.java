package com.hamusuke.packetcap.highlight.instruction;

import io.netty.buffer.ByteBuf;

public class SameRefInstructionImpl<B extends ByteBuf, T> implements SameRefInstruction<B, T> {
    private final int highlightIndex;

    public SameRefInstructionImpl(int highlightIndex) {
        this.highlightIndex = highlightIndex;
    }

    @Override
    public int getHighlightIndex() {
        return this.highlightIndex;
    }
}
