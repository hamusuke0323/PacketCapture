package com.hamusuke.packetcap.clazz.field;

import com.hamusuke.packetcap.clazz.visitor.ClassVisitor;

public interface MapField extends ClassField {
    ClassVisitor getKeyVisitor();

    @Override
    default String getDescription() {
        return this.getKeyVisitor() + "->" + this.getVisitor();
    }
}
