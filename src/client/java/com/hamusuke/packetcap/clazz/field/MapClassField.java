package com.hamusuke.packetcap.clazz.field;

import com.hamusuke.packetcap.clazz.visitor.ClassVisitor;

public class MapClassField implements MapField {
    private final ClassVisitor keyVisitor;
    private final ClassVisitor valueVisitor;

    public MapClassField(Object key, Object value) {
        this.keyVisitor = ClassField.findClassVisitor(key);
        this.valueVisitor = ClassField.findClassVisitor(value);
    }

    @Override
    public ClassVisitor getKeyVisitor() {
        return this.keyVisitor;
    }

    @Override
    public ClassVisitor getVisitor() {
        return this.valueVisitor;
    }

    @Override
    public String toString() {
        return this.getDescription();
    }
}
