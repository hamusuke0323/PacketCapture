package com.hamusuke.packetcap.clazz.field;

import com.google.common.primitives.Primitives;
import com.hamusuke.packetcap.clazz.visitor.*;
import com.hamusuke.packetcap.utils.ObjectUtil;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;

public interface ClassField {
    @Nullable
    static ClassVisitor findClassVisitor(Object obj) {
        if (obj == null) {
            return null;
        }

        var r = ClassVisitors.getRegistry();
        for (int i = r.size() - 1; i >= 0; i--) {
            var e = r.get(i);
            if (!e.finder().test(obj)) {
                continue;
            }

            return e.factory().create(e.caster().apply(obj));
        }

        return new ClassVisitor(obj.getClass(), obj);
    }

    @Nullable
    default ClassVisitor getVisitor() {
        return null;
    }

    String getDescription();

    default String getName() {
        return this.getDescription();
    }

    boolean isStatic();
}
