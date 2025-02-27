package com.hamusuke.packetcap.highlight.instruction;

import com.hamusuke.packetcap.highlight.DataHighlightInstruction.DataHighlightInstructionBuilder;
import com.hamusuke.packetcap.highlight.Highlight;
import com.mojang.datafixers.util.Either;
import io.netty.buffer.ByteBuf;
import org.apache.commons.compress.utils.Lists;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static com.hamusuke.packetcap.highlight.Highlight.getWrittenByteLen;

public class MapInstruction<B extends ByteBuf, T, K, V, M extends Map<K, V>> extends RecursiveInstruction<B, T, M> {
    public MapInstruction(Either<Function<T, M>, Function<B, M>> mapGetter, BufInstruction<? super B, M> preProcessor, BufInstruction<? super B, K> keyInstruction, BufInstruction<? super B, V> valueInstruction, Function<M, String> descriptor) {
        super((curWriterIndex, receivedByteBuf, buf, map) -> {
            List<Highlight<?>> highlights = Lists.newArrayList();

            var entry = DataHighlightInstructionBuilder.<B, Entry<K, V>>builder()
                    .compoundField(DataHighlightInstructionBuilder.<B, K>builder()
                            .field(keyInstruction, Function.identity()).build(), k -> "Key", Entry::getKey)
                    .compoundField(DataHighlightInstructionBuilder.<B, V>builder()
                            .field(valueInstruction, Function.identity()).build(), v -> "Value", Entry::getValue)
                    .build();

            var pre = preProcessor.write(curWriterIndex, receivedByteBuf, buf, map);
            highlights.addAll(pre);
            curWriterIndex += getWrittenByteLen(pre);

            if (receivedByteBuf != null) {
                receivedByteBuf.readerIndex(curWriterIndex);
            }

            var counter = new AtomicInteger();
            for (var e : map.entrySet()) {
                var mapHighlighter = DataHighlightInstructionBuilder.<B, M>builder()
                        .compoundField(entry, ignored -> "Index: " + counter.getAndIncrement(), m -> e)
                        .build();
                var hs = mapHighlighter.write(curWriterIndex, receivedByteBuf, buf, map);
                highlights.addAll(hs);
                curWriterIndex += getWrittenByteLen(hs);

                if (receivedByteBuf != null) {
                    receivedByteBuf.readerIndex(curWriterIndex);
                }
            }

            return highlights;
        }, descriptor, mapGetter);
    }
}
