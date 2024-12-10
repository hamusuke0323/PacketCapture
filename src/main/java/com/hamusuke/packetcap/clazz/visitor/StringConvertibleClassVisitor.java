package com.hamusuke.packetcap.clazz.visitor;

public class StringConvertibleClassVisitor extends ClassVisitor {
    public StringConvertibleClassVisitor(Class<?> clazz, Object instance) {
        super(clazz, instance);
    }

    @Override
    public synchronized void visitClass() {
    }

    @Override
    public final boolean visitable() {
        return false;
    }

    @Override
    public final boolean isStringConvertibleClass() {
        return true;
    }
}
