package com.hamusuke.packetcap.utils;

import javax.annotation.Nullable;

public class ByteConversion {
    public static String convertBytes(long bytes) {
        if (bytes < 0) {
            return convertBytes(Long.MAX_VALUE);
        }

        var curSize = Size.B;
        var remainBytes = (double) bytes;

        while ((remainBytes / 1024.0D) >= 1.0D && curSize.next() != null) {
            curSize = curSize.next();
            remainBytes /= 1024.0D;
        }

        return "%.1f %s".formatted(remainBytes, curSize);
    }

    private enum Size {
        B,
        KB,
        MB,
        GB,
        TB,
        PB,
        EB;

        @Nullable
        private Size next() {
            var next = this.ordinal() + 1;
            var v = values();
            return this == EB ? null : v[next];
        }
    }
}
