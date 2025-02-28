package com.hamusuke.packetcap.highlight.instruction;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.encoding.VarInts;
import net.minecraft.network.encoding.VarLongs;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.function.Function;

import static com.hamusuke.packetcap.highlight.instruction.BufInstruction.constant;
import static com.hamusuke.packetcap.highlight.instruction.BufInstruction.valueOnly;

public class BasicInstructions {
    public static final Descriptor<ByteBuf, Boolean> BOOL = o -> constant(1, o);
    public static final Descriptor<ByteBuf, Byte> BYTE = o -> constant(1, o);
    public static final Descriptor<ByteBuf, Short> SHORT = o -> constant(2, o);
    public static final Descriptor<ByteBuf, Float> FLOAT = o -> constant(4, o);
    public static final Descriptor<ByteBuf, Double> DOUBLE = o -> constant(8, o);
    public static final Descriptor<ByteBuf, Integer> INT = o -> constant(4, o);
    public static final Descriptor<ByteBuf, Long> LONG = o -> constant(8, o);
    public static final Descriptor<ByteBuf, long[]> LONG_ARRAY = o -> valueOnly(longs -> longs.length * 8, o);
    public static final Descriptor<ByteBuf, byte[]> BYTE_ARRAY = o -> valueOnly(bytes -> bytes.length, o);
    public static final Descriptor<ByteBuf, ByteBuf> BYTE_BUF = o -> valueOnly(ByteBuf::readableBytes, o);
    public static final Descriptor<ByteBuf, ByteBuffer> BYTE_BUFFER = o -> valueOnly(Buffer::remaining, o);
    public static final Descriptor<ByteBuf, Integer> VAR_INT = o -> valueOnly(VarInts::getSizeInBytes, o);
    public static final Descriptor<ByteBuf, Long> VAR_LONG = o -> valueOnly(VarLongs::getSizeInBytes, o);
    public static final Descriptor<ByteBuf, UUID> UUID = o -> constant(16, o);

    public static Function<Boolean, String> prefixed(String prefix) {
        return b -> prefix + ": " + (b ? "true" : "false");
    }

    public interface Descriptor<B extends ByteBuf, T> {
        BufInstruction<B, T> withDescription(Function<T, String> descriptor);

        default BufInstruction<B, T> noDesc() {
            return this.withDescription(t -> "");
        }

        default <O> BufInstruction<B, O> xmap(final Function<? super O, ? extends T> from) {
            return this.noDesc().xmap(from);
        }
    }
}
