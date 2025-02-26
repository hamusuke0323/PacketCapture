package com.hamusuke.packetcap.highlight.instruction;

import com.google.common.collect.ImmutableList;
import com.hamusuke.packetcap.highlight.Highlight;
import com.hamusuke.packetcap.highlight.Highlight.HighlightRange;
import com.mojang.datafixers.util.Either;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hamusuke.packetcap.highlight.Highlight.getWrittenByteLen;

public class RecursiveInstruction<B extends ByteBuf, V, T> extends TransformingInstruction<B, V, T> {
    public RecursiveInstruction(BufInstruction<? super B, T> instruction, Function<T, String> descriptor, Function<V, T> fieldGetter) {
        this(instruction, descriptor, Either.left(fieldGetter));
    }

    public RecursiveInstruction(BufInstruction<? super B, T> instruction, Function<T, String> descriptor, Either<Function<V, T>, Function<B, T>> fieldGetter) {
        this(instruction, descriptor, fieldGetter, null);
    }

    public RecursiveInstruction(BufInstruction<? super B, T> instruction, Function<T, String> descriptor, Either<Function<V, T>, Function<B, T>> fieldGetter, @Nullable Predicate<T> shouldContinue) {
        super((curWriterIndex, receivedByteBuf, buf, value) -> {
            List<Highlight<?>> subHighlights = instruction.write(curWriterIndex, receivedByteBuf, buf, value);
            int written = getWrittenByteLen(subHighlights);

            if (receivedByteBuf != null) {
                receivedByteBuf.readerIndex(curWriterIndex + written);
            }

            var h = new Highlight<>(new HighlightRange(curWriterIndex, curWriterIndex + written - 1), value, descriptor.apply(value), ImmutableList.copyOf(subHighlights));
            return Collections.singletonList(h);
        }, fieldGetter, shouldContinue);
    }
}
