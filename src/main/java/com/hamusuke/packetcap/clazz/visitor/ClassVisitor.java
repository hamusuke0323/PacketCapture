package com.hamusuke.packetcap.clazz.visitor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.hamusuke.packetcap.clazz.field.ClassField;
import com.hamusuke.packetcap.clazz.field.SimpleClassField;

import java.lang.reflect.Field;
import java.util.List;

public class ClassVisitor {
    protected final Class<?> clazz;
    protected final String className;
    protected final Object instance;
    protected final List<ClassField> fields = Lists.newArrayList();
    protected boolean visited;

    public ClassVisitor(Class<?> clazz, Object instance) {
        this.clazz = clazz;
        this.className = getClassName(clazz);
        this.instance = instance;
    }

    private static List<Field> getFields(Class<?> clazz) {
        var fields = Lists.newArrayList(clazz.getDeclaredFields());
        var superclass = clazz.getSuperclass();
        if (superclass != null) {
            fields.addAll(getFields(superclass));
        }

        return fields;
    }

    private static String getClassName(Class<?> clazz) {
        var stringBuilder = new StringBuilder();
        var enclosingClass = clazz.getEnclosingClass();
        if (enclosingClass != null) {
            stringBuilder.append(getClassName(enclosingClass)).append('$');
        }

        return stringBuilder.append(clazz.getSimpleName()).toString();
    }

    protected synchronized void visitClass() {
        var fields = getFields(this.clazz);
        for (var field : fields) {
            this.fields.add(new SimpleClassField(field, this.instance));
        }
    }

    public final synchronized void visit() {
        if (!this.fields.isEmpty() || !this.visitable()) {
            return;
        }

        this.visited = true;
        this.visitClass();
    }

    public String getClassName() {
        return this.className;
    }

    public String getFullClassName() {
        return this.clazz.getPackageName() + "." + this.getClassName();
    }

    public ImmutableList<ClassField> getFields() {
        return ImmutableList.copyOf(this.fields);
    }

    public boolean visitable() {
        return !this.visited;
    }

    public boolean isStringConvertibleClass() {
        return false;
    }

    @Override
    public String toString() {
        var fields = this.getFields();
        return fields.isEmpty() || this.isStringConvertibleClass() ? this.instance.toString() : this.getClassName() + fields;
    }
}
