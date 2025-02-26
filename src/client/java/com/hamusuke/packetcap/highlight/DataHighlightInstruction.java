package com.hamusuke.packetcap.highlight;

import com.google.common.collect.ImmutableList;
import com.hamusuke.packetcap.highlight.instruction.BasicInstructions.Descriptor;
import com.hamusuke.packetcap.highlight.instruction.*;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketEncoder;
import net.minecraft.network.codec.ValueFirstEncoder;
import org.apache.commons.compress.utils.Lists;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hamusuke.packetcap.highlight.Highlight.getWrittenByteLen;
import static com.hamusuke.packetcap.highlight.instruction.BasicInstructions.VAR_INT;

public class DataHighlightInstruction<B extends ByteBuf, V> implements BufInstruction<B, V> {
    private final List<BufInstruction<? super B, V>> instructions;

    private DataHighlightInstruction(List<BufInstruction<? super B, V>> instructions) {
        this.instructions = instructions;
    }

    @Override
    public List<Highlight<?>> write(int curWriterIndex, @Nullable B receivedByteBuf, B buf, V value) {
        List<Highlight<?>> highlights = Lists.newArrayList();

        for (var i : this.instructions) {
            var hs = i.write(curWriterIndex, receivedByteBuf, buf, value);
            highlights.addAll(hs);
            curWriterIndex += getWrittenByteLen(hs);

            if (receivedByteBuf != null) {
                receivedByteBuf.readerIndex(curWriterIndex);
            }

            if (!i.shouldContinue(receivedByteBuf, value)) {
                break;
            }
        }

        return highlights;
    }

    public static class DataHighlightInstructionBuilder<B extends ByteBuf, T> {
        private final List<BufInstruction<? super B, T>> instructions = Lists.newArrayList();

        private DataHighlightInstructionBuilder() {
        }

        public static <B extends ByteBuf, T> DataHighlightInstructionBuilder<B, T> builder() {
            return new DataHighlightInstructionBuilder<>();
        }

        public <V> DataHighlightInstructionBuilder<B, T> constant(Descriptor<? super B, V> descriptor) {
            return this.constant(descriptor.withDescription(v -> ""));
        }

        public <V> DataHighlightInstructionBuilder<B, T> constant(BufInstruction<? super B, V> instruction) {
            this.instructions.add(new ConstantInstruction<>(instruction));
            return this;
        }

        public <V> DataHighlightInstructionBuilder<B, T> field(Descriptor<? super B, V> descriptor, Function<T, V> fieldGetter) {
            return this.field(descriptor, fieldGetter, v -> true);
        }

        public <V> DataHighlightInstructionBuilder<B, T> field(Descriptor<? super B, V> descriptor, Function<T, V> fieldGetter, Predicate<V> shouldContinue) {
            return this.field(descriptor.withDescription(v -> ""), fieldGetter, shouldContinue);
        }

        public <V> DataHighlightInstructionBuilder<B, T> field(BufInstruction<? super B, V> instruction, Function<T, V> fieldGetter) {
            return this.field(instruction, fieldGetter, v -> true);
        }

        public <V> DataHighlightInstructionBuilder<B, T> field(BufInstruction<? super B, V> instruction, Function<T, V> fieldGetter, Predicate<V> shouldContinue) {
            this.instructions.add(new TransformingInstruction<>(instruction, fieldGetter, shouldContinue));
            return this;
        }

        public <V, C extends Collection<V>> DataHighlightInstructionBuilder<B, T> listWithSize(Descriptor<? super B, V> elementInstruction, Function<T, C> collectionGetter) {
            return this.listWithSize(elementInstruction, c -> "", collectionGetter);
        }

        public <V, C extends Collection<V>> DataHighlightInstructionBuilder<B, T> listWithSize(Descriptor<? super B, V> elementInstruction, Function<C, String> collectionDescriptor, Function<T, C> collectionGetter) {
            return this.listWithSize(DataHighlightInstructionBuilder.<B, V>builder()
                    .field(elementInstruction.withDescription(Objects::toString), Function.identity()).build(), collectionDescriptor, collectionGetter);
        }

        public <V, C extends Collection<V>> DataHighlightInstructionBuilder<B, T> listWithSize(BufInstruction<? super B, V> elementInstruction, Function<T, C> collectionGetter) {
            return this.listWithSize(elementInstruction, c -> "", collectionGetter);
        }

        public <V, C extends Collection<V>> DataHighlightInstructionBuilder<B, T> listWithSize(BufInstruction<? super B, V> elementInstruction, Function<C, String> collectionDescriptor, Function<T, C> collectionGetter) {
            return this.listWithSize(DataHighlightInstructionBuilder.<B, V>builder()
                    .field(elementInstruction, Function.identity()).build(), collectionDescriptor, collectionGetter);
        }

        public <V, C extends Collection<V>> DataHighlightInstructionBuilder<B, T> listWithSize(DataHighlightInstruction<? super B, V> elementInstruction, Function<T, C> collectionGetter) {
            return this.listWithSize(elementInstruction, c -> "", collectionGetter);
        }

        public <V, C extends Collection<V>> DataHighlightInstructionBuilder<B, T> listWithSize(DataHighlightInstruction<? super B, V> elementInstruction, Function<C, String> collectionDescriptor, Function<T, C> collectionGetter) {
            return this.listWithSize(elementInstruction, collectionDescriptor, Either.left(collectionGetter));
        }

        public <V, C extends Collection<V>> DataHighlightInstructionBuilder<B, T> listWithSize(DataHighlightInstruction<? super B, V> elementInstruction, Function<C, String> collectionDescriptor, Either<Function<T, C>, Function<B, C>> collectionGetter) {
            this.instructions.add(new ListInstruction<>(collectionGetter, new TransformingInstruction<>(
                    VAR_INT.withDescription(size -> "Collection Size: " + size),
                    Collection::size,
                    null
            ), elementInstruction, collectionDescriptor));
            return this;
        }

        public <V, C extends Collection<V>> DataHighlightInstructionBuilder<B, T> list(DataHighlightInstruction<? super B, V> elementInstruction, Function<C, String> collectionDescriptor, Function<T, C> collectionGetter) {
            this.instructions.add(new ListInstruction<>(Either.left(collectionGetter), (curWriterIndex, receivedByteBuf, buf, value) -> List.of(), elementInstruction, collectionDescriptor));
            return this;
        }

        public <K, V, M extends Map<K, V>> DataHighlightInstructionBuilder<B, T> mapWithSize(DataHighlightInstruction<? super B, K> keyInstruction, DataHighlightInstruction<? super B, V> valueInstruction, Function<M, String> mapDescriptor, Either<Function<T, M>, Function<B, M>> mapGetter) {
            this.instructions.add(new MapInstruction<>(mapGetter, new TransformingInstruction<>(
                    VAR_INT.withDescription(size -> "Collection Size: " + size),
                    Map::size,
                    null
            ), keyInstruction, valueInstruction, mapDescriptor));
            return this;
        }

        public <V> DataHighlightInstructionBuilder<B, T> jsonCodec(Codec<V> codec, Function<V, String> descriptor, Function<T, V> fieldGetter) {
            this.instructions.add(BufInstruction.writeAndGuess((byteBuf, o) -> ((PacketByteBuf) byteBuf).encodeAsJson(codec, fieldGetter.apply(o)), o -> descriptor.apply(fieldGetter.apply(o))));
            return this;
        }

        public <V> DataHighlightInstructionBuilder<B, T> packetCodec(PacketCodec<B, V> codec, Function<T, V> fieldGetter) {
            return this.packetCodec(codec, v -> "", fieldGetter);
        }

        public <V> DataHighlightInstructionBuilder<B, T> packetCodec(PacketCodec<B, V> codec, Function<V, String> descriptor, Function<T, V> fieldGetter) {
            this.instructions.add(BufInstruction.writeAndGuess((byteBuf, o) -> codec.encode(byteBuf, fieldGetter.apply(o)), o -> descriptor.apply(fieldGetter.apply(o))));
            return this;
        }

        public <V> DataHighlightInstructionBuilder<B, T> packetEncoderV(ValueFirstEncoder<B, V> codec, Function<T, V> fieldGetter) {
            return this.packetEncoderV(codec, v -> "", fieldGetter);
        }

        public <V> DataHighlightInstructionBuilder<B, T> packetEncoderV(ValueFirstEncoder<B, V> codec, Function<V, String> descriptor, Function<T, V> fieldGetter) {
            this.instructions.add(BufInstruction.writeAndGuess((byteBuf, o) -> codec.encode(fieldGetter.apply(o), byteBuf), o -> descriptor.apply(fieldGetter.apply(o))));
            return this;
        }

        public <V> DataHighlightInstructionBuilder<B, T> packetEncoder(PacketEncoder<B, V> codec, Function<T, V> fieldGetter) {
            return this.packetEncoder(codec, v -> "", fieldGetter);
        }

        public <V> DataHighlightInstructionBuilder<B, T> packetEncoder(PacketEncoder<B, V> codec, Function<V, String> descriptor, Function<T, V> fieldGetter) {
            this.instructions.add(BufInstruction.writeAndGuess((byteBuf, o) -> codec.encode(byteBuf, fieldGetter.apply(o)), o -> descriptor.apply(fieldGetter.apply(o))));
            return this;
        }

        public <T2> DataHighlightInstructionBuilder<B, T> sub(DataHighlightInstruction<? super B, T2> subInstruction, Function<T, T2> fieldGetter) {
            return this.sub(subInstruction, t2 -> "", fieldGetter);
        }

        public <T2> DataHighlightInstructionBuilder<B, T> sub(DataHighlightInstruction<? super B, T2> subInstruction, Function<T2, String> descriptor, Function<T, T2> fieldGetter) {
            this.instructions.add(new RecursiveInstruction<>(subInstruction, descriptor, Either.left(fieldGetter)));
            return this;
        }

        public DataHighlightInstruction<B, T> build() {
            return new DataHighlightInstruction<B, T>(ImmutableList.copyOf(this.instructions));
        }
    }
}
