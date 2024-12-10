package com.hamusuke.packetcap.clazz.visitor;

import com.hamusuke.packetcap.clazz.field.ArrayClassField;

public class ArrayVisitor extends ClassVisitor {
    public ArrayVisitor(Class<?> clazz, Object[] instance) {
        super(clazz, instance);
    }

    @Override
    protected synchronized void visitClass() {
        for (var obj : (Object[]) this.instance) {
            this.fields.add(new ArrayClassField(obj));
        }
    }
}
