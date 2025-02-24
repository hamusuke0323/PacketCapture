package com.hamusuke.packetcap.clazz.field;

import com.hamusuke.packetcap.PacketCapture;
import com.hamusuke.packetcap.clazz.visitor.ClassVisitor;
import com.hamusuke.packetcap.clazz.visitor.StringConvertibleClassVisitor;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class SimpleClassField implements ClassField {
    private final String fieldName;
    @Nullable
    private final ClassVisitor visitor;
    private final boolean isStatic;
    private final boolean errorOccurred;

    public SimpleClassField(Field field, Object instance) {
        String name;
        boolean error = false;
        ClassVisitor visitor = null;
        boolean isStatic = false;

        try {
            name = field.getName();
            isStatic = Modifier.isStatic(field.getModifiers());

            if (field.trySetAccessible()) {
                var obj = field.get(instance);
                visitor = ClassField.findClassVisitor(obj);
            } else {
                visitor = new StringConvertibleClassVisitor(instance.getClass(), instance);
            }
        } catch (Throwable e) {
            error = true;
            name = "Could not access the field: " + e.getMessage();
        }

        this.fieldName = PacketCapture.getInstance().deobfuscate(name);
        this.visitor = visitor;
        this.errorOccurred = error;
        this.isStatic = isStatic;
    }

    @Override
    @Nullable
    public ClassVisitor getVisitor() {
        return this.visitor;
    }

    @Override
    public String getDescription() {
        if (this.errorOccurred) {
            return this.fieldName;
        }

        return this.fieldName + " = " + this.getVisitor();
    }

    @Override
    public String getName() {
        return this.fieldName;
    }

    @Override
    public String toString() {
        return this.getDescription();
    }

    @Override
    public boolean isStatic() {
        return this.isStatic;
    }
}
