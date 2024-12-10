package com.hamusuke.packetcap.clazz.visitor;

import java.util.Collection;

public class CollectionVisitor extends ArrayVisitor {
    public CollectionVisitor(Class<?> clazz, Collection<?> instance) {
        super(clazz, instance.toArray());
    }
}
