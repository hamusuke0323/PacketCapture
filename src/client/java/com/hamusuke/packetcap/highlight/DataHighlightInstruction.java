package com.hamusuke.packetcap.highlight;

import com.google.common.collect.ImmutableList;
import com.hamusuke.packetcap.highlight.Highlight.HighlightRange;
import com.hamusuke.packetcap.highlight.instruction.BasicInstructions.Descriptor;
import com.hamusuke.packetcap.highlight.instruction.BufInstruction;
import com.hamusuke.packetcap.highlight.instruction.SubBufInstruction;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketEncoder;
import org.apache.commons.compress.utils.Lists;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hamusuke.packetcap.highlight.instruction.BasicInstructions.VAR_INT;
import static com.hamusuke.packetcap.highlight.instruction.BufInstruction.guessing;

public class DataHighlightInstruction<B extends ByteBuf, V> {
    private final List<BufInstruction<? super ByteBuf, Object>> instructions;

    private DataHighlightInstruction(List<BufInstruction<? super ByteBuf, Object>> instructions) {
        this.instructions = instructions;
    }

    public List<Highlight<?>> createHighlights(int curWriterIndex, B buf, V value) {
        List<Highlight<?>> highlights = Lists.newArrayList();

        for (var i : this.instructions) {
            if (i instanceof SubBufInstruction sub) {
                var instance = sub.getField(value);
                List<Highlight<?>> hs = i.write(curWriterIndex, buf, value);
                int start = curWriterIndex;
                curWriterIndex += hs.stream()
                        .map(Highlight::range)
                        .mapToInt(r -> r.endInclusive() - r.startInclusive() + 1).sum();
                var h = new Highlight<>(new HighlightRange(start, curWriterIndex - 1), instance, "", ImmutableList.copyOf(hs));
                highlights.add(h);
            } else {
                var hs = i.write(curWriterIndex, buf, value);
                highlights.addAll(hs);
                curWriterIndex += hs.stream()
                        .map(Highlight::range)
                        .mapToInt(r -> r.endInclusive() - r.startInclusive() + 1).sum();
            }

            if (!i.shouldContinue(value)) {
                break;
            }
        }

        return highlights;
    }

    public static class PacketDataHighlighterBuilder<B extends ByteBuf, T> {
        private final List<BufInstruction<? super ByteBuf, Object>> instructions = Lists.newArrayList();

        private PacketDataHighlighterBuilder() {
        }

        public static <B extends ByteBuf, T> PacketDataHighlighterBuilder<B, T> builder() {
            return new PacketDataHighlighterBuilder<>();
        }

        public <V> PacketDataHighlighterBuilder<B, T> constant(Descriptor<? super B, V> descriptor) {
            return this.constant(descriptor.withDescription(v -> ""));
        }

        public <V> PacketDataHighlighterBuilder<B, T> constant(BufInstruction<? super B, V> instruction) {
            this.instructions.add((BufInstruction<? super ByteBuf, Object>) instruction);
            return this;
        }

        public <V> PacketDataHighlighterBuilder<B, T> field(Descriptor<? super B, V> descriptor, Function<T, V> fieldGetter) {
            return this.field(descriptor, fieldGetter, v -> true);
        }

        public <V> PacketDataHighlighterBuilder<B, T> field(Descriptor<? super B, V> descriptor, Function<T, V> fieldGetter, Predicate<V> shouldContinue) {
            return this.field(descriptor.withDescription(v -> ""), fieldGetter, shouldContinue);
        }

        public <V> PacketDataHighlighterBuilder<B, T> field(BufInstruction<? super B, V> instruction, Function<T, V> fieldGetter) {
            return this.field(instruction, fieldGetter, v -> true);
        }

        public <V> PacketDataHighlighterBuilder<B, T> field(BufInstruction<? super B, V> instruction, Function<T, V> fieldGetter, Predicate<V> shouldContinue) {
            this.instructions.add((BufInstruction) new BufInstruction<B, T>() {
                @Override
                public List<Highlight<?>> write(int curWriterIndex, B buf, T value) {
                    return instruction.write(curWriterIndex, buf, fieldGetter.apply(value));
                }

                @Override
                public boolean shouldContinue(T value) {
                    return shouldContinue.test(fieldGetter.apply(value));
                }
            });
            return this;
        }

        public <V, C extends Collection<V>> PacketDataHighlighterBuilder<B, T> list(Descriptor<? super B, V> elementInstruction, Function<T, C> collectionGetter) {
            return this.list(PacketDataHighlighterBuilder.<B, V>builder()
                    .field(elementInstruction.withDescription(Objects::toString), o -> o).build(), collectionGetter);
        }

        public <V, C extends Collection<V>> PacketDataHighlighterBuilder<B, T> list(DataHighlightInstruction<B, V> elementInstruction, Function<T, C> collectionGetter) {
            this.instructions.add((BufInstruction) new SubBufInstruction<B, T, C>() {
                @Override
                public List<Highlight<?>> write(int curWriterIndex, B buf, T value) {
                    var collection = collectionGetter.apply(value);
                    List<Highlight<?>> highlights = Lists.newArrayList();
                    highlights.addAll(VAR_INT.withDescription(size -> "Collection Size: " + size).write(curWriterIndex, buf, collection.size()));
                    curWriterIndex += highlights.stream()
                            .map(Highlight::range)
                            .mapToInt(r -> r.endInclusive() - r.startInclusive() + 1).sum();

                    for (var v : collection) {
                        var hs = elementInstruction.createHighlights(curWriterIndex, buf, v);
                        highlights.addAll(hs);
                        curWriterIndex += hs.stream()
                                .map(Highlight::range)
                                .mapToInt(r -> r.endInclusive() - r.startInclusive() + 1).sum();
                    }

                    return highlights;
                }

                @Override
                public C getField(T value) {
                    return collectionGetter.apply(value);
                }
            });
            return this;
        }

        public <V> PacketDataHighlighterBuilder<B, T> jsonCodec(Codec<V> codec, Function<V, String> descriptor, Function<T, V> fieldGetter) {
            this.instructions.add(guessing((byteBuf, o) -> ((PacketByteBuf) byteBuf).encodeAsJson(codec, fieldGetter.apply((T) o)), o -> descriptor.apply(fieldGetter.apply((T) o))));
            return this;
        }

        public <V> PacketDataHighlighterBuilder<B, T> packetCodec(PacketCodec<B, V> codec, Function<T, V> fieldGetter) {
            return this.packetCodec(codec, v -> "", fieldGetter);
        }

        public <V> PacketDataHighlighterBuilder<B, T> packetCodec(PacketCodec<B, V> codec, Function<V, String> descriptor, Function<T, V> fieldGetter) {
            this.instructions.add(guessing((byteBuf, o) -> codec.encode((B) byteBuf, fieldGetter.apply((T) o)), o -> descriptor.apply(fieldGetter.apply((T) o))));
            return this;
        }

        public <V> PacketDataHighlighterBuilder<B, T> packetEncoder(PacketEncoder<B, V> codec, Function<T, V> fieldGetter) {
            return this.packetEncoder(codec, v -> "", fieldGetter);
        }

        public <V> PacketDataHighlighterBuilder<B, T> packetEncoder(PacketEncoder<B, V> codec, Function<V, String> descriptor, Function<T, V> fieldGetter) {
            this.instructions.add(guessing((byteBuf, o) -> codec.encode((B) byteBuf, fieldGetter.apply((T) o)), o -> descriptor.apply(fieldGetter.apply((T) o))));
            return this;
        }

        public <T2> PacketDataHighlighterBuilder<B, T> sub(DataHighlightInstruction<? super B, T2> subInstruction, Function<T, T2> fieldGetter) {
            this.instructions.add((BufInstruction) new SubBufInstruction<B, T, T2>() {
                @Override
                public List<Highlight<?>> write(int curWriterIndex, B buf, T value) {
                    return subInstruction.createHighlights(curWriterIndex, buf, this.getField(value));
                }

                @Override
                public T2 getField(T value) {
                    return fieldGetter.apply(value);
                }
            });

            return this;
        }

        public DataHighlightInstruction<B, T> build() {
            return new DataHighlightInstruction<>(ImmutableList.copyOf(this.instructions));
        }
    }
}
