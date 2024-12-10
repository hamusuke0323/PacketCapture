package com.hamusuke.packetcap.clazz.field;

import com.hamusuke.packetcap.clazz.visitor.ClassVisitor;

public class ArrayClassField implements ClassField {
    private final Object obj;
    private final ClassVisitor visitor;

    public ArrayClassField(Object oneOfInstanceInArray) {
        this.obj = oneOfInstanceInArray;
        this.visitor = ClassField.findClassVisitor(oneOfInstanceInArray);
    }

    @Override
    public ClassVisitor getVisitor() {
        return this.visitor;
    }

    @Override
    public String getDescription() {
        return this.obj.toString();
    }

    @Override
    public String toString() {
        return this.getDescription();
    }
}
