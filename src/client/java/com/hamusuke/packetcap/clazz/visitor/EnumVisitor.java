package com.hamusuke.packetcap.clazz.visitor;

public class EnumVisitor extends StringConvertibleClassVisitor {
    public EnumVisitor(Class<?> clazz, Enum<?> instance) {
        super(clazz, instance);
    }

    @Override
    public String toString() {
        return this.instance.toString() + " = " + ((Enum<?>) this.instance).ordinal();
    }
}
