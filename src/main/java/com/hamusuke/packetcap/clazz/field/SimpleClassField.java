package com.hamusuke.packetcap.clazz.field;

import com.hamusuke.packetcap.PacketCapture;
import com.hamusuke.packetcap.clazz.visitor.ClassVisitor;
import com.hamusuke.packetcap.clazz.visitor.StringConvertibleClassVisitor;

import javax.annotation.Nullable;
import java.lang.reflect.Field;

public class SimpleClassField implements ClassField {
    private final String fieldName;
    @Nullable
    private final ClassVisitor visitor;
    private final boolean errorOccurred;

    public SimpleClassField(Field field, Object instance) {
        String name;
        boolean error = false;
        ClassVisitor visitor = null;

        try {
            name = field.getName();

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
    public String toString() {
        return this.getDescription();
    }
}
