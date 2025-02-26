package com.hamusuke.packetcap.highlight.instruction;

import com.hamusuke.packetcap.highlight.Highlight;
import com.mojang.datafixers.util.Either;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hamusuke.packetcap.highlight.Highlight.getWrittenByteLen;

public class TransformingInstruction<B extends ByteBuf, T, V> implements BufInstruction<B, T> {
    protected final BufInstruction<? super B, V> instruction;
    protected final Either<Function<T, V>, Function<B, V>> transformer;
    @Nullable
    protected final Predicate<V> shouldContinue;

    public TransformingInstruction(BufInstruction<? super B, V> instruction, Function<T, V> transformer, @Nullable Predicate<V> shouldContinue) {
        this(instruction, Either.left(transformer), shouldContinue);
    }

    public TransformingInstruction(BufInstruction<? super B, V> instruction, Either<Function<T, V>, Function<B, V>> transformer, @Nullable Predicate<V> shouldContinue) {
        this.instruction = instruction;
        this.transformer = transformer;
        this.shouldContinue = shouldContinue;
    }

    @Override
    public List<Highlight<?>> write(int curWriterIndex, @Nullable B receivedByteBuf, B buf, T value) {
        var transformedValue = this.transform(receivedByteBuf, value);
        var hs = this.instruction.write(curWriterIndex, receivedByteBuf, buf, transformedValue);
        if (receivedByteBuf != null) {
            receivedByteBuf.readerIndex(curWriterIndex + getWrittenByteLen(hs));
        }
        return hs;
    }

    protected V transform(@Nullable B receivedByteBuf, T value) {
        return this.transformer.map(l -> l.apply(value), r -> r.apply(receivedByteBuf));
    }

    @Override
    public boolean shouldContinue(@Nullable B receivedByteBuf, T value) {
        return this.shouldContinue == null || this.shouldContinue.test(this.transform(receivedByteBuf, value));
    }
}
