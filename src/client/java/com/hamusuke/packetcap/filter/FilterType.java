package com.hamusuke.packetcap.filter;

import java.util.function.BiFunction;

public enum FilterType {
    EQUALS(String::equalsIgnoreCase),
    CONTAINS(String::contains);

    private final BiFunction<String, String, Boolean> matcher;

    FilterType(BiFunction<String, String, Boolean> matcher) {
        this.matcher = matcher;
    }

    public boolean isTrash(String packetName, String filter) {
        return this.matcher.apply(packetName, filter);
    }

    public FilterType next() {
        var v = values();
        var next = this.ordinal() + 1;
        next = v.length <= next ? 0 : next;
        return v[next];
    }
}
