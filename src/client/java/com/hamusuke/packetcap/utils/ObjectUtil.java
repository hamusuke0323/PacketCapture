package com.hamusuke.packetcap.utils;

import org.apache.commons.compress.utils.Lists;

import java.lang.reflect.Array;
import java.util.List;

public class ObjectUtil {
    public static Object[] toArray(Object array) {
        List<Object> out = Lists.newArrayList();
        var l = Array.getLength(array);
        for (int i = 0; i < l; i++) {
            out.add(Array.get(array, i));
        }

        return out.toArray();
    }
}
