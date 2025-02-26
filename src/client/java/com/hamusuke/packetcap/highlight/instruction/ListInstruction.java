package com.hamusuke.packetcap.highlight.instruction;

import com.hamusuke.packetcap.highlight.Highlight;
import com.hamusuke.packetcap.highlight.Highlight.HighlightRange;
import com.mojang.datafixers.util.Either;
import io.netty.buffer.ByteBuf;
import org.apache.commons.compress.utils.Lists;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import static com.hamusuke.packetcap.highlight.Highlight.getWrittenByteLen;

public class ListInstruction<B extends ByteBuf, T, E, C extends Collection<E>> extends RecursiveInstruction<B, T, C> {
    public ListInstruction(Either<Function<T, C>, Function<B, C>> collectionGetter, BufInstruction<? super B, C> preProcessor, BufInstruction<? super B, E> elementInstruction, Function<C, String> descriptor) {
        super((curWriterIndex, receivedByteBuf, buf, collection) -> {
            List<Highlight<?>> highlights = Lists.newArrayList();

            var pre = preProcessor.write(curWriterIndex, receivedByteBuf, buf, collection);
            highlights.addAll(pre);
            curWriterIndex += getWrittenByteLen(pre);

            if (receivedByteBuf != null) {
                receivedByteBuf.readerIndex(curWriterIndex);
            }

            int i = 0;
            for (var e : collection) {
                int start = curWriterIndex;
                var hs = elementInstruction.write(curWriterIndex, receivedByteBuf, buf, e);
                curWriterIndex += getWrittenByteLen(hs);

                if (receivedByteBuf != null) {
                    receivedByteBuf.readerIndex(curWriterIndex);
                }

                var indexed = new Highlight<>(new HighlightRange(start, curWriterIndex - 1), e, "Index: " + i, hs);
                highlights.add(indexed);
                i++;
            }

            return highlights;
        }, descriptor, collectionGetter);
    }
}
