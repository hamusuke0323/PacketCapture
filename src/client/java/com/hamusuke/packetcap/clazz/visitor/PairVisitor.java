package com.hamusuke.packetcap.clazz.visitor;

import com.mojang.datafixers.util.Pair;

import java.util.Map;

public class PairVisitor extends MapVisitor {
    public PairVisitor(Class<?> clazz, Pair<?, ?> instance) {
        super(clazz, Map.of(instance.getFirst(), instance.getSecond()));
    }
}
