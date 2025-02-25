package com.hamusuke.packetcap.highlight.instruction;

import com.hamusuke.packetcap.highlight.Highlight;
import com.hamusuke.packetcap.highlight.Highlight.HighlightRange;
import io.netty.buffer.ByteBuf;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public interface BufInstruction<B extends ByteBuf, V> {
    List<Highlight<?>> write(int curWriterIndex, B buf, V value);

    default boolean shouldContinue(V value) {
        return true;
    }

    static <B extends ByteBuf, V> BufInstruction<B, V> constant(int bytes, Function<V, String> descriptor) {
        return (curWriterIndex, buf, value) -> Collections.singletonList(new Highlight<>(new HighlightRange(curWriterIndex, bytes + curWriterIndex - 1), value, descriptor.apply(value), List.of()));
    }

    static <B extends ByteBuf, V> BufInstruction<B, V> valueOnly(Function<V, Integer> writtenByteGetter, Function<V, String> descriptor) {
        return (curWriterIndex, buf, value) -> Collections.singletonList(new Highlight<>(new HighlightRange(curWriterIndex, writtenByteGetter.apply(value) + curWriterIndex - 1), value, descriptor.apply(value), List.of()));
    }

    static <B extends ByteBuf, V> BufInstruction<B, V> guessing(BiConsumer<B, V> writer, Function<V, String> descriptor) {
        return guessing((b, v) -> {
        }, writer, descriptor);
    }

    static <B extends ByteBuf, V> BufInstruction<B, V> guessing(BiConsumer<B, V> exclusionForRangeWriter, BiConsumer<B, V> writer, Function<V, String> descriptor) {
        return (curWriterIndex, buf, value) -> {
            int start = buf.writerIndex();
            exclusionForRangeWriter.accept(buf, value);
            var diff = buf.writerIndex() - start - 1;
            start = buf.writerIndex();
            writer.accept(buf, value);
            diff = buf.writerIndex() - start - 1 - diff - 1;
            return diff < 0 ? List.of() : Collections.singletonList(new Highlight<>(new HighlightRange(curWriterIndex, curWriterIndex + diff), value, descriptor.apply(value), List.of()));
        };
    }
}
