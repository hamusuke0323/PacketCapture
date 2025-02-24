package com.hamusuke.packetcap.clazz.field;

import com.hamusuke.packetcap.clazz.visitor.ClassVisitor;
import com.hamusuke.packetcap.clazz.visitor.ClassVisitors;
import org.jetbrains.annotations.Nullable;

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
