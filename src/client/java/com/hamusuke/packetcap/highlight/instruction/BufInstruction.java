package com.hamusuke.packetcap.highlight.instruction;

import com.hamusuke.packetcap.highlight.Highlight;
import com.hamusuke.packetcap.highlight.Highlight.HighlightRange;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

@FunctionalInterface
public interface BufInstruction<B extends ByteBuf, V> {
    static <B extends ByteBuf, V> BufInstruction<B, V> constant(int bytes, Function<V, String> descriptor) {
        return (curWriterIndex, received, buf, value) -> {
            if (received != null) {
                received.readerIndex(curWriterIndex + bytes);
            }

            return Collections.singletonList(new Highlight<>(new HighlightRange(curWriterIndex, bytes + curWriterIndex - 1), value, descriptor.apply(value), List.of()));
        };
    }

    static <B extends ByteBuf, V> BufInstruction<B, V> valueOnly(Function<V, Integer> writtenByteGetter, Function<V, String> descriptor) {
        return (curWriterIndex, received, buf, value) -> {
            int bytes = writtenByteGetter.apply(value);
            if (received != null) {
                received.readerIndex(curWriterIndex + bytes);
            }

            return Collections.singletonList(new Highlight<>(new HighlightRange(curWriterIndex, bytes + curWriterIndex - 1), value, descriptor.apply(value), List.of()));
        };
    }

    static <B extends ByteBuf, V> BufInstruction<B, V> writeAndGuess(BiConsumer<B, V> writer, Function<V, String> descriptor) {
        return writeAndGuess((b, v) -> {
        }, writer, descriptor);
    }

    static <B extends ByteBuf, V> BufInstruction<B, V> writeAndGuess(BiConsumer<B, V> exclusionForRangeWriter, BiConsumer<B, V> writer, Function<V, String> descriptor) {
        return (curWriterIndex, received, buf, value) -> {
            int start = buf.writerIndex();
            exclusionForRangeWriter.accept(buf, value);
            var diff = buf.writerIndex() - start - 1;
            start = buf.writerIndex();
            writer.accept(buf, value);
            diff = buf.writerIndex() - start - 1 - diff - 1;
            if (diff >= 0 && received != null) {
                received.readerIndex(curWriterIndex + diff + 1);
            }

            return diff < 0 ? List.of() : Collections.singletonList(new Highlight<>(new HighlightRange(curWriterIndex, curWriterIndex + diff), value, descriptor.apply(value), List.of()));
        };
    }

    static <B extends ByteBuf, V> BufInstruction<B, V> readAndGuess(Consumer<B> reader, Function<V, String> descriptor) {
        return readAndGuess((b) -> {
        }, reader, descriptor);
    }

    static <B extends ByteBuf, V> BufInstruction<B, V> readAndGuess(Consumer<B> exclusionForRangeReader, Consumer<B> reader, Function<V, String> descriptor) {
        return (curWriterIndex, buf, ignored, value) -> {
            exclusionForRangeReader.accept(buf);
            var start = Objects.requireNonNull(buf).readerIndex();
            reader.accept(buf);
            var diff = buf.readerIndex() - start - 1;
            return diff < 0 ? List.of() : Collections.singletonList(new Highlight<>(new HighlightRange(curWriterIndex, curWriterIndex + diff), value, descriptor.apply(value), List.of()));
        };
    }

    /**
     *
     * @param curWriterIndex current reader index.
     * @param receivedByteBuf if S2C packet, this param contains the received data.
     * @param buf feel free to use this buf.
     * @param value something value.
     * @return List of Highlight.
     */
    List<Highlight<?>> write(int curWriterIndex, @Nullable B receivedByteBuf, B buf, V value);

    default boolean shouldContinue(@Nullable B receivedByteBuf, V value) {
        return true;
    }
}
