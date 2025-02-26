package com.hamusuke.packetcap.highlight;

import java.util.List;

public record Highlight<T>(HighlightRange range, T instance, String description,
                           List<Highlight<?>> sub) {
    public static int getWrittenByteLen(List<Highlight<?>> highlights) {
        return highlights.stream()
                .map(Highlight::range)
                .mapToInt(HighlightRange::byteCount).sum();
    }

    public record HighlightRange(int startInclusive, int endInclusive) {
        public boolean contains(int pos) {
            return this.startInclusive <= pos && pos <= this.endInclusive;
        }

        public int byteCount() {
            return Math.max(0, this.endInclusive - this.startInclusive + 1);
        }
    }
}
