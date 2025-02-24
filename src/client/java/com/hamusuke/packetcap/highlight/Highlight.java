package com.hamusuke.packetcap.highlight;

import java.util.List;

public record Highlight<T>(HighlightRange range, T instance, String description,
                           List<Highlight<?>> sub) {
    public record HighlightRange(int startInclusive, int endInclusive) {
        public boolean contains(int pos) {
            return this.startInclusive <= pos && pos <= this.endInclusive;
        }
    }
}
