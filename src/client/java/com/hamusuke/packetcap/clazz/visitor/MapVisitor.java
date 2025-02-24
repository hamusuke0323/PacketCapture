package com.hamusuke.packetcap.clazz.visitor;

import com.hamusuke.packetcap.clazz.field.MapClassField;

import java.util.Map;

public class MapVisitor extends ClassVisitor {
    public MapVisitor(Class<?> clazz, Map<?, ?> instance) {
        super(clazz, instance);
    }

    @Override
    public synchronized void visitClass() {
        this.fields.addAll(((Map<?, ?>) this.instance).entrySet().stream().map(entry -> new MapClassField(entry.getKey(), entry.getValue())).toList());
    }
}
